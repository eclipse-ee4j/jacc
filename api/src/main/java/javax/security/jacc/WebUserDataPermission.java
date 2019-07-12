/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package javax.security.jacc;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.security.Permission;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Class for Jakarta Servlet Web user data permissions. A WebUserDataPermission is a named permission and has actions.
 *
 * <p>
 * The name of a WebUserDataPermission (also referred to as the target name) identifies a Web resource by its context
 * path relative URL pattern.
 *
 * @see Permission
 *
 * @author Ron Monzillo
 * @author Gary Ellison
 *
 */
public final class WebUserDataPermission extends Permission {

    private static final long serialVersionUID = -970193775626385011L;

    private transient static final String EMPTY_STRING = "";
    private transient static final String ESCAPED_COLON = "%3A";

    private static String transportKeys[] = { "NONE", "INTEGRAL", "CONFIDENTIAL", };
    private static Map<String, Integer> transportHash = new HashMap<String, Integer>();
    static {
        for (int i = 0; i < transportKeys.length; i++) {
            transportHash.put(transportKeys[i], i);
        }
    }

    private static int TT_NONE = transportHash.get("NONE");
    private static int TT_CONFIDENTIAL = transportHash.get("CONFIDENTIAL");

    private transient URLPatternSpec urlPatternSpec;
    private transient HttpMethodSpec methodSpec;
    private transient int transportType;
    private transient int hashCodeValue;


    /**
     * The serialized fields of this permission are defined below. Whether or not the serialized fields correspond to actual
     * (private) fields is an implementation decision.
     *
     * @serialField actions String the canonicalized actions string (as returned by getActions).
     */
    private static final ObjectStreamField[] serialPersistentFields = { new ObjectStreamField("actions", String.class) };

    /**
     * Creates a new WebUserDataPermission with the specified name and actions.
     *
     * <p>
     * The name contains a URLPatternSpec that identifies the web resources to which the permissions applies. The syntax of
     * a URLPatternSpec is as follows:
     *
     * <pre>
     *
     *          URLPatternList ::= URLPattern | URLPatternList colon URLPattern
     *
     *          URLPatternSpec ::= null | URLPattern | URLPattern colon URLPatternList
     *
     * </pre>
     *
     * <p>
     * A null URLPatternSpec is translated to the default URLPattern, "/", by the permission constructor. The empty string
     * is an exact URLPattern, and may occur anywhere in a URLPatternSpec that an exact URLPattern may occur. The first
     * URLPattern in a URLPatternSpec may be any of the pattern types, exact, path-prefix, extension, or default as defined
     * in the <i>Jakarta Servlet Specification)</i>. When a URLPatternSpec includes a URLPatternList, the patterns of the
     * URLPatternList identify the resources to which the permission does NOT apply and depend on the pattern type and value
     * of the first pattern as follows:
     *
     * <ul>
     * <li>No pattern may exist in the URLPatternList that matches the first pattern.
     * <li>If the first pattern is a path-prefix pattern, only exact patterns matched by the first pattern and path-prefix
     * patterns matched by, but different from, the first pattern may occur in the URLPatternList.
     * <li>If the first pattern is an extension pattern, only exact patterns that are matched by the first pattern and
     * path-prefix patterns may occur in the URLPatternList.
     * <li>If the first pattern is the default pattern, "/", any pattern except the default pattern may occur in the
     * URLPatternList.
     * <li>If the first pattern is an exact pattern a URLPatternList must not be present in the URLPatternSpec.
     * </ul>
     *
     * <p>
     * The actions parameter contains a comma separated list of HTTP methods that may be followed by a transportType
     * separated from the HTTP method by a colon.
     *
     * <pre>
     *
     *          ExtensionMethod ::= any token as defined by RFC 2616
     *                  (that is, 1*[any CHAR except CTLs or separators])
     *
     *          HTTPMethod ::= "Get" | "POST" | "PUT" | "DELETE" | "HEAD" |
     *                  "OPTIONS" | "TRACE" | ExtensionMethod
     *
     *          HTTPMethodList ::= HTTPMethod | HTTPMethodList comma HTTPMethod
     *
     *          HTTPMethodExceptionList ::= exclaimationPoint HTTPMethodList
     *
     *          HTTPMethodSpec ::= emptyString | HTTPMethodExceptionList |
     *                  HTTPMethodList
     *
     *          transportType ::= "INTEGRAL" | "CONFIDENTIAL" | "NONE"
     *
     *          actions ::= null | HTTPMethodSpec |
     *                  HTTPMethodSpec colon transportType
     *
     * </pre>
     *
     * <p>
     * If duplicates occur in the HTTPMethodSpec they must be eliminated by the permission constructor.
     *
     * <p>
     * An empty string HTTPMethodSpec is a shorthand for a List containing all the possible HTTP methods.
     *
     * <p>
     * If the HTTPMethodSpec contains an HTTPMethodExceptionList (i.e., it begins with an exclaimationPoint), the permission
     * pertains to all methods except those occuring in the exception list.
     *
     * <p>
     * An actions string without a transportType is a shorthand for a actions string with the value "NONE" as its
     * TransportType.
     *
     * <p>
     * A granted permission representing a transportType of "NONE", indicates that the associated resources may be accessed
     * using any connection type.
     *
     * @param name the URLPatternSpec that identifies the application specific web resources to which the permission
     * pertains. All URLPatterns in the URLPatternSpec are relative to the context path of the deployed web application
     * module, and the same URLPattern must not occur more than once in a URLPatternSpec. A null URLPatternSpec is
     * translated to the default URLPattern, "/", by the permission constructor. All colons occuring within the URLPattern
     * elements of the URLPatternSpec must be represented in escaped encoding as defined in RFC 2396.
     * @param actions identifies the HTTP methods and transport type to which the permission pertains. If the value passed
     * through this parameter is null or the empty string, then the permission is constructed with actions corresponding to
     * all the possible HTTP methods and transportType "NONE".
     */
    public WebUserDataPermission(String name, String actions) {
        super(name);
        this.urlPatternSpec = new URLPatternSpec(name);
        parseActions(actions);
    }

    /**
     * Creates a new WebUserDataPermission with name corresponding to the URLPatternSpec, and actions composed from the
     * array of HTTP methods and the transport type.
     *
     * @param urlPatternSpec the URLPatternSpec that identifies the application specific web resources to which the
     * permission pertains. All URLPatterns in the URLPatternSpec are relative to the context path of the deployed web
     * application module, and the same URLPattern must not occur more than once in a URLPatternSpec. A null URLPatternSpec
     * is translated to the default URLPattern, "/", by the permission constructor. All colons occurring within the
     * URLPattern elements of the URLPatternSpec must be represented in escaped encoding as defined in RFC 2396.
     * @param HTTPMethods an array of strings each element of which contains the value of an HTTP method. If the value
     * passed through this parameter is null or is an array with no elements, then the permission is constructed with
     * actions corresponding to all the possible HTTP methods.
     * @param transportType a String whose value is a transportType. If the value passed through this parameter is null,
     * then the permission is constructed with actions corresponding to transportType "NONE".
     */
    public WebUserDataPermission(String urlPatternSpec, String[] HTTPMethods, String transportType) {
        super(urlPatternSpec);
        this.urlPatternSpec = new URLPatternSpec(urlPatternSpec);

        this.transportType = TT_NONE;

        if (transportType != null) {
            Integer bit = transportHash.get(transportType);
            if (bit == null) {
                throw new IllegalArgumentException("illegal transport value");
            }
            this.transportType = bit.intValue();
        }

        this.methodSpec = HttpMethodSpec.getSpec(HTTPMethods);
    }

    /**
     * Creates a new WebUserDataPermission from the HttpServletRequest object.
     *
     * @param request the HttpServletRequest object corresponding to the Jakarta Servlet operation to which the permission pertains.
     * The permission name is the substring of the requestURI (HttpServletRequest.getRequestURI()) that begins after the
     * contextPath (HttpServletRequest.getContextPath()). When the substring operation yields the string "/", the permission
     * is constructed with the empty string as its name. The constructor must transform all colon characters occurring in the
     * name to escaped encoding as defined in RFC 2396. The HTTP method component of the permission's actions is as obtained
     * from HttpServletRequest.getMethod(). The TransportType component of the permission's actions is determined by calling
     * HttpServletRequest.isSecure().
     */
    public WebUserDataPermission(HttpServletRequest request) {
        super(getUriMinusContextPath(request));
        this.urlPatternSpec = new URLPatternSpec(super.getName());
        this.transportType = request.isSecure() ? TT_CONFIDENTIAL : TT_NONE;
        this.methodSpec = HttpMethodSpec.getSpec(request.getMethod());
    }

    /**
     * Checks two WebUserDataPermission objects for equality. WebUserDataPermission objects are equivalent if their
     * URLPatternSpec and (canonicalized) actions values are equivalent.
     *
     * <p>
     * The URLPatternSpec of a reference permission is
     * equivalent to that of an argument permission if their first patterns are equivalent, and the patterns of the
     * URLPatternList of the reference permission collectively match exactly the same set of patterns as are matched by the
     * patterns of the URLPatternList of the argument permission.
     *
     * <p>
     * Two Permission objects, P1 and P2, are equivalent if and only if P1.implies(P2) AND P2.implies(P1).
     *
     * @param o the WebUserDataPermission object being tested for equality with this WebUserDataPermission.
     * @return true if the argument WebUserDataPermission object is equivalent to this WebUserDataPermission.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof WebUserDataPermission)) {
            return false;
        }

        WebUserDataPermission that = (WebUserDataPermission) o;

        if (this.transportType != that.transportType) {
            return false;
        }

        if (!this.methodSpec.equals(that.methodSpec)) {
            return false;
        }

        return this.urlPatternSpec.equals(that.urlPatternSpec);
    }

    /**
     * Returns a canonical String representation of the actions of this WebUserDataPermission.
     *
     * <p>
     * The canonical form of the actions of a WebUserDataPermission is described by the following syntax description.
     *
     * <pre>
     *
     *          ExtensionMethod ::= any token as defined by RFC 2616
     *                   (that is, 1*[any CHAR except CTLs or separators])
     *
     *          HTTPMethod ::= "GET" | "POST" | "PUT" | "DELETE" | "HEAD" |
     *                   "OPTIONS" | "TRACE" | ExtensionMethod
     *
     *          HTTPMethodList ::= HTTPMethod | HTTPMethodList comma HTTPMethod
     *
     *          HTTPMethodExceptionList ::= exclaimationPoint HTTPMethodList
     *
     *          HTTPMethodSpec ::= emptyString | HTTPMethodExceptionList |
     *                  HTTPMethodList
     *
     *          transportType ::= "INTEGRAL" | "CONFIDENTIAL" | "NONE"
     *
     *          actions ::= null | HTTPMethodList |
     *                  HTTPMethodSpec colon transportType
     *
     * </pre>
     *
     * <p>
     * If the permission's HTTP methods correspond to the entire HTTP method set and the permission's transport type is
     * "INTEGRAL" or "CONFIDENTIAL", the HTTP methods shall be represented in the canonical form by an emptyString
     * HTTPMethodSpec. If the permission's HTTP methods correspond to the entire HTTP method set, and the permission's
     * transport type is not "INTEGRAL"or "CONFIDENTIAL", the canonical actions value shall be the null value.
     *
     * <p>
     * If the permission's methods do not correspond to the entire HTTP method set, duplicates must be eliminated and the
     * remaining elements must be ordered such that the predefined methods preceed the extension methods, and such that
     * within each method classification the corresponding methods occur in ascending lexical order. The resulting
     * (non-emptyString) HTTPMethodSpec must be included in the canonical form, and if the permission's transport type is
     * not "INTEGRAL" or "CONFIDENTIAL", the canonical actions value must be exactly the resulting HTTPMethodSpec.
     *
     * @return a String containing the canonicalized actions of this WebUserDataPermission (or the null value).
     */
    @Override
    public String getActions() {
        String methodSpecActions = methodSpec.getActions();

        if (transportType == TT_NONE && methodSpecActions == null) {
            return null;
        }

        if (transportType == TT_NONE) {
            return methodSpecActions;
        }

        if (methodSpecActions == null) {
            return ":" + transportKeys[transportType];
        }

        return methodSpecActions + ":" + transportKeys[transportType];
    }

    /**
     * Returns the hash code value for this WebUserDataPermission.
     *
     * <p>
     * The properties of the returned hash code must be as follows:
     *
     * <ul>
     * <li>During the lifetime of a Java application, the hashCode method shall return the same integer value every time it
     * is called on a WebUserDataPermission object. The value returned by hashCode for a particular EJBMethod permission
     * need not remain consistent from one execution of an application to another.
     * <li>If two WebUserDataPermission objects are equal according to the equals method, then calling the hashCode method
     * on each of the two Permission objects must produce the same integer result (within an application).
     * </ul>
     *
     * @return the integer hash code value for this object.
     */
    @Override
    public int hashCode() {
        if (hashCodeValue == 0) {
            String hashInput = urlPatternSpec.toString() + " " + methodSpec.hashCode() + ":" + transportType;
            hashCodeValue = hashInput.hashCode();
        }

        return hashCodeValue;
    }

    /**
     * Determines if the argument Permission is "implied by" this WebUserDataPermission.
     *
     * <p>
     * For this to be the case all of the following must be true:
     *
     * <ul>
     * <li>The argument is an instanceof WebUserDataPermission.
     * <li>The first URLPattern in the name of the argument permission is matched by the first URLPattern in the name of
     * this permission.
     * <li>The first URLPattern in the name of the argument permission is NOT matched by any URLPattern in the
     * URLPatternList of the URLPatternSpec of this permission.
     * <li>If the first URLPattern in the name of the argument permission matches the first URLPattern in the URLPatternSpec
     * of this permission, then every URLPattern in the URLPatternList of the URLPatternSpec of this permission is matched
     * by a URLPattern in the URLPatternList of the argument permission.
     * <li>The HTTP methods represented by the actions of the argument permission are a subset of the HTTP methods
     * represented by the actions of this permission.
     * <li>The transportType in the actions of this permission either corresponds to the value "NONE", or equals the
     * transportType in the actions of the argument permission.
     * </ul>
     *
     * <p>
     * URLPattern matching is performed using the <i>Jakarta Servlet matching rules</i> where two URL patterns match if they are
     * related as follows:
     *
     * <ul>
     * <li>their pattern values are String equivalent, or
     * <li>this pattern is the path-prefix pattern "/*", or
     * <li>this pattern is a path-prefix pattern (that is, it starts with "/" and ends with "/*") and the argument pattern
     * starts with the substring of this pattern, minus its last 2 characters, and the next character of the argument
     * pattern, if there is one, is "/", or
     * <li>this pattern is an extension pattern (that is, it starts with "*.") and the argument pattern ends with this
     * pattern, or
     * <li>the reference pattern is the special default pattern, "/", which matches all argument patterns.
     * </ul>
     *
     * <p>
     * All of the comparisons described above are case sensitive.
     *
     * @param permission "this" WebUserDataPermission is checked to see if it implies the argument permission.
     *
     * @return true if the specified permission is implied by this object, false if not.
     */
    @Override
    public boolean implies(Permission permission) {
        if (!(permission instanceof WebUserDataPermission)) {
            return false;
        }

        WebUserDataPermission that = (WebUserDataPermission) permission;

        if (this.transportType != TT_NONE && this.transportType != that.transportType) {
            return false;
        }

        if (!this.methodSpec.implies(that.methodSpec)) {
            return false;
        }

        return this.urlPatternSpec.implies(that.urlPatternSpec);
    }

    // ----------------- Private Methods ---------------------

    /**
     * Chops the ContextPath off the front of the requestURI to yield the servletPath + PathInfo. For the special case where
     * the servletPath + PathInfo is the pattern, "/", this routine returns the empty string.
     */
    private static String getUriMinusContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();

        if (uri == null) {
            return EMPTY_STRING;
        }

        String contextPath = request.getContextPath();
        int contextLength = contextPath == null ? 0 : contextPath.length();

        if (contextLength > 0) {
            uri = uri.substring(contextLength);
        }

        if (uri.equals("/")) {
            return EMPTY_STRING;
        }

        // Encode all colons
        return uri.replaceAll(":", ESCAPED_COLON);
    }

    private void parseActions(String actions) {
        transportType = TT_NONE;

        if (actions == null || actions.equals("")) {
            methodSpec = HttpMethodSpec.getSpec((String) null);
        } else {
            int colon = actions.indexOf(':');
            if (colon < 0) {
                methodSpec = HttpMethodSpec.getSpec(actions);
            } else {
                if (colon == 0) {
                    methodSpec = HttpMethodSpec.getSpec((String) null);
                } else {
                    methodSpec = HttpMethodSpec.getSpec(actions.substring(0, colon));
                }

                Integer bit = transportHash.get(actions.substring(colon + 1));
                if (bit == null) {
                    throw new IllegalArgumentException("illegal transport value");
                }

                transportType = bit.intValue();
            }
        }
    }

    /**
     * readObject reads the serialized fields from the input stream and uses them to restore the permission. This method
     * need not be implemented if establishing the values of the serialized fields (as is done by defaultReadObject) is
     * sufficient to initialize the permission.
     *
     * @param inputStream The stream from which the fields are read
     *
     * @throws ClassNotFoundException If the class of an object couldn't be found
     * @throws IOException If an I/O error occurs
     */
    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        parseActions((String) inputStream.readFields().get("actions", null));
        urlPatternSpec = new URLPatternSpec(super.getName());
    }

    /**
     * writeObject is used to establish the values of the serialized fields before they are written to the output stream and
     * need not be implemented if the values of the serialized fields are always available and up to date. The serialized
     * fields are written to the output stream in the same form as they would be written by defaultWriteObject.
     *
     * @param outputStream The stream to which the serialized fields are written
     *
     * @throws IOException If an I/O error occurs while writing to the underlying stream
     */
    private synchronized void writeObject(ObjectOutputStream outputStream) throws IOException {
        outputStream.putFields().put("actions", this.getActions());
        outputStream.writeFields();
    }

}
