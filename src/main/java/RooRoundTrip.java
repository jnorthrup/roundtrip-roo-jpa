

             /*
    RooRoundTrip
    Copyright (C) 2010 James Northrup

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
              */
import org.projname.model.Item;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.persistence.Entity;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 *
 * java -cp ... RooRoundTrip [projectname [package]]
 * roo < target/com.projname.model.roo
 */
public class RooRoundTrip {
  private static final String GET = "get";
//    private static final Package PACKAGE = Item.class.getPackage();
    private static final Package PACKAGE = Item.class.getPackage();
  private static final String PROJNAME = "projname";
  private static final String MANY = "*";
  private static final List<String> BASIC = Arrays.asList(new String[]{
      "boolean", "date", "number", "string"});

  private static final List<String> IGNORED = Arrays.asList(new String[]{
      "id", "version", "class"});
  private static String PROJECTNAME = PROJNAME;
  private static final List<String> AVOID_DEP = Arrays.asList("junit", "log4j",
      "servlet-api", "flexjson", "spring-js-resources", "commons-digester",
      "commons-fileupload", "jstl", "el-api", "joda-time", "jsp-api",
      "gwt-servlet", "gwt-user", "json", "gin", "validation-api", "xalan",
      "hibernate-validator", "cglib-nodep", "jta", "commons-pool",
      "commons-dbcp", "hsqldb", "hibernate-core", "hibernate-entitymanager",
      "hibernate-jpa-2.0-api");
  public static ArrayList<Class<?>> classes = new ArrayList<Class<?>>();

  /**
   *
   * @param args [PROJECTNAME[PACKAGENAME]]
   */
  public static void main(String[] args) throws ClassNotFoundException {

    List<Class> classes = new ArrayList<Class>();
    ArrayList<File> directories = new ArrayList<File>();
    String pckgname = PACKAGE.getName();
    try {

      ClassLoader cld = Thread.currentThread().getContextClassLoader();
      if (cld == null) {
        throw new ClassNotFoundException("Can't get class loader.");
      }
      // Ask for all resources for the path
      final String resName = pckgname.replace('.', '/');
      Enumeration<URL> resources = cld.getResources(resName);
      while (resources.hasMoreElements()) {
        URL res = resources.nextElement();
        if (res.getProtocol().equalsIgnoreCase("jar")
            || res.getProtocol().equalsIgnoreCase("zip")) {
          JarURLConnection conn = (JarURLConnection) res.openConnection();
          JarFile jar = conn.getJarFile();

          for (JarEntry e2 : Collections.list(jar.entries())) {
            if (e2.getName().startsWith(resName)
                && e2.getName().endsWith(".class")
                && !e2.getName().contains("$")) {
              String className = e2.getName().replace("/", ".").substring(0,
                  e2.getName().length() - 6);
              System.out.println(className);
              classes.add(Class.forName(className));
            }
          }
        } else
          directories.add(new File(URLDecoder.decode(res.getPath(), "UTF-8")));
      }
    } catch (NullPointerException x) {
      throw new ClassNotFoundException(pckgname + " does not appear to be "
          + "a valid package (Null pointer exception)");
    } catch (UnsupportedEncodingException encex) {
      throw new ClassNotFoundException(pckgname + " does not appear to be "
          + "a valid package (Unsupported encoding)");
    } catch (IOException ioex) {
      throw new ClassNotFoundException("IOException was thrown when trying "
          + "to get all resources for " + pckgname);
    }

    // For every directory identified capture all the .class files
    for (File directory : directories) {
      if (directory.exists()) {
        // Get the list of the files contained in the package
        String[] files = directory.list();
        for (String file : files) {
          // we are only interested in .class files
          if (file.endsWith(".class")) {
            // removes the .class extension
            final Class<?> e2 = Class.forName(pckgname + '.'
                + file.substring(0, file.length() - 6));
            if (e2.isAnnotationPresent(Entity.class)) {
              classes.add(e2);
            }
          }
        }
      } else {
        throw new ClassNotFoundException(pckgname + " (" + directory.getPath()
            + ") does not appear to be a valid package");
      }
    }
    RooRoundTrip.classes.addAll((Collection<? extends Class<?>>) classes);

    ArrayList<Class<?>> classSet = RooRoundTrip.classes;

    LinkedHashMap<Class<?>, Map<Class<?>, String>> methodMap = new LinkedHashMap<Class<?>, Map<Class<?>, String>>();

    for (Class entity : classSet) {
      Map<Class<?>, String> classMethodMap = new LinkedHashMap<Class<?>, String>();

      methodMap.put(entity, classMethodMap);
      final Method[] declaredMethods = entity.getMethods();
      for (Method method : declaredMethods) {
        String methodName = method.getName();
        if (methodName.startsWith(GET)) {
          String name;
          name = methodName.substring(GET.length());
          name = Character.toLowerCase(name.charAt(0)) + name.substring(1);

          if (!IGNORED.contains(name)) {
            try {
              final Class returnType = method.getReturnType();
              Class key = null;
              if (Collection.class.isAssignableFrom(returnType)) {

                final Type genericReturnType = method.getGenericReturnType();
                for (Type actualTypeArgument : ((ParameterizedType) genericReturnType).getActualTypeArguments()) {
                  key = (Class) actualTypeArgument;
                  name = MANY + name;
                  break;
                }
              } else
                key = (returnType);

              if (classMethodMap.containsKey(key))
                classMethodMap.put(key, classMethodMap.get(key) + " " + name);
              else
                classMethodMap.put(key, name);
            } catch (Exception e) {
            }
          }
        }
      }
    }

    PrintWriter out;
    Writer writer = null;
    try {
      writer = new FileWriter(MessageFormat.format("target/{0}.roo",
          PACKAGE.getName()));

    } catch (Exception e) {
      try {
        writer = new FileWriter(PACKAGE.getName() + ".roo");
      } catch (IOException e1) {
        e1.printStackTrace(); //Todo: verify for a purpose
      }

    }
    out = new PrintWriter(writer);

    out.println(MessageFormat.format(
        "//project --topLevelPackage {0} --java 6 --projectName {1}",
        args.length > 1 ? args[1] : PACKAGE.getName().substring(0,PACKAGE.getName().lastIndexOf('.')), PROJECTNAME));
    out.println(MessageFormat.format(
        "// persistence setup --provider OPENJPA --database   HYPERSONIC_IN_MEMORY --applicationId {0} --persistenceUnit {1}",
        PROJECTNAME, PROJECTNAME));

    //    out.println("// " + methodMap.toString());

    for (Class eclass : methodMap.keySet()) {
      final String canonicalName = eclass.getCanonicalName();
      PROJECTNAME = args.length > 0 ? args[0] : PROJNAME;
      out.println(MessageFormat.format(
          "entity  --persistenceUnit {0} --class {1}", PROJECTNAME,
          canonicalName));

    }

    for (Map.Entry<Class<?>, Map<Class<?>, String>> methodEntries : methodMap.entrySet()) {
      final Class<?> key1 = methodEntries.getKey();
      final String canonicalName = key1.getCanonicalName();

      out.println(MessageFormat.format("focus --class {0}", canonicalName));
      for (Map.Entry<Class<?>, String> classStringEntry : methodEntries.getValue().entrySet()) {
        final Class<?> key = classStringEntry.getKey();
        final String value = classStringEntry.getValue();
        //field boolean      field date         field email        field embedded     field enum         field number
        //field other        field reference    field set          field string
        final String[] split = value.split(" ");
        for (String s : split) {
          String lcaseAttrType = key.getSimpleName().toLowerCase();
          if (classSet.contains(key))//entity
          {
            final boolean b = s.startsWith(MANY);
            out.println(MessageFormat.format(
                "field {0} --fieldName {1} --{2} {3}", b ? "set" : "reference",
                (b ? s.substring(1) : s), b ? "element" : "type",
                key.getCanonicalName()));
          } else if ("date".equals(lcaseAttrType)
              || Calendar.class.getSimpleName().equals(lcaseAttrType))
            out.println(MessageFormat.format(
                "field date  --type {0} --fieldName {1}",
                key.getCanonicalName(), s));
          else if (BASIC.contains(lcaseAttrType)) {
            out.println(MessageFormat.format("field {0}  --fieldName {1}",
                lcaseAttrType, s));
          } else {
            out.println(MessageFormat.format(
                "field {0} --fieldName {1} --type {2}",
                key.getPackage() != Object.class.getPackage() ? "reference"
                    : "other", s, key.getCanonicalName()));
          }
        }
      }
    }

    try {

      DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
      domFactory.setNamespaceAware(true); // never forget this!
      DocumentBuilder builder = domFactory.newDocumentBuilder();
      Document doc = builder.parse("pom.xml");

      XPathFactory factory = XPathFactory.newInstance();
      XPath xpath = factory.newXPath();
      xpath.setNamespaceContext(new NamespaceContext() {
        @Override
        public String getNamespaceURI(String prefix) {
          return "http://maven.apache.org/POM/4.0.0";
        }

        @Override
        public String getPrefix(String namespaceURI) {
          return "pom"; //todo: review for a purpose
        }

        @Override
        public Iterator getPrefixes(String namespaceURI) {
          return Arrays.asList("pom").listIterator(); //To change body of implemented methods use File | Settings | File Templates.
        }
      });

      XPathExpression expr;
      Object result;
      NodeList nodes;
      expr = xpath.compile("/pom:project/pom:properties/*");

      result = expr.evaluate(doc, XPathConstants.NODESET);
      nodes = (NodeList) result;
      int length;
      length = nodes.getLength();
      for (int i = 0; i < length; i++) {
        Element node = (Element) nodes.item(i);
        final String textContent = node.getTextContent().trim();
        if (!textContent.isEmpty()) {
          out.println(MessageFormat.format(
              "properties set --name {0}.properties --path ROOT --key {1} --value {2}",
              PROJECTNAME, node.getNodeName(), textContent));
        }
      }
      expr = xpath.compile("/pom:project/pom:dependencies/pom:dependency");
      result = expr.evaluate(doc, XPathConstants.NODESET);

      nodes = (NodeList) result;
      length = nodes.getLength();
      String version = null;
      for (int i = 0; i < length; i++) {
        Element node = (Element) nodes.item(i);
        version = node.getElementsByTagName("version").item(0).getTextContent();

        final String artifactId = node.getElementsByTagName("artifactId").item(
            0).getTextContent();
        if (!(version.startsWith("$") || AVOID_DEP.contains(artifactId))) {//skip the configs with managed pom

          final String groupId = node.getElementsByTagName("groupId").item(0).getTextContent();

          out.println(MessageFormat.format(
              "//dependency add --artifactId {0} --groupId {1} --version {2}",
              artifactId, groupId, version));
        }
      }

      for (Class<?> aClass : methodMap.keySet()) {

        out.println("test integration --entity " + aClass.getCanonicalName());
      }
      out.println("//gwt setup");

    } catch (ParserConfigurationException e) {
      e.printStackTrace(); //Todo: verify for a purpose
    } catch (SAXException e) {
      e.printStackTrace(); //Todo: verify for a purpose
    } catch (IOException e) {
      e.printStackTrace(); //Todo: verify for a purpose
    } catch (XPathExpressionException e) {
      e.printStackTrace(); //Todo: verify for a purpose
    } finally {
    }

    out.close();
  }
}
