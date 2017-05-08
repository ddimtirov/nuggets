/*
 *    Copyright 2017 by Dimitar Dimitrov
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.github.ddimitrov.nuggets;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;

import static io.github.ddimitrov.nuggets.Exceptions.rethrow;
import static io.github.ddimitrov.nuggets.Functions.fallback;

/**
 * <p>URL schema handlers - a quick way to add support for data URLs and
 * Classpath URLs to your project.</p>
 *
 * <p>The official {@link java.net.URLStreamHandlerFactory URLStreamHandlerFactory}
 * extension API has a number of drawbacks, the most significant of which that there
 * can only be one.</p>
 *
 * <p>An alternative, suggested way is to keep using the default implementation
 * ({@link sun.misc.Launcher.Factory}) and just plant your Handler classes in a package
 * following the default convention {@code sun.net.www.protocol.<schema>}.</p>
 *
 * <p>For example, to add support for data URLs and cp URLs under the
 * schemas cp:com/foobar/MyResource.txt, add the following 2 files:</p>
 * <ul>
 *     <li><b>{@code File: <src-root>/sun/net/www/protocol/data/Handler.java}</b>
 *         <pre><code>
 * package sun.net.www.protocol.data;      // the schema is the last part of the package
 * public class Handler extends DataUrl {} // the class name needs to be Handler
 *         </code></pre>
 *     </li>
 *     <li><b>{@code File: <src-root>/sun/net/www/protocol/cp/Handler.java}</b>
 *         <pre><code>
 * package sun.net.www.protocol.cp;
 * public class Handler extends UrlStreamHandlers.ResolversUrl {
 *    public Handler() { super(Foobar.class.getClassLoader::getResource, ClassLoader::getSystemResource); }
 * }
 *         </code></pre>
 *     </li>
 * </ul>
 *
 * <p>With this setup you don't need to set any system properties or muck
 * with factories. Also, this way all protocols shipped with the JRE will
 * keep working.</p>
 *
 */
public final class UrlStreamHandlers {
    static final Charset US_ASCII = Charset.forName("US-ASCII");
    static final Charset UTF8 = Charset.forName("UTF-8");

    private UrlStreamHandlers() {}

    /**
     * Factory method for URL-encoded data-URLs from a string.
     * These have the advantages that they are more readable when few special characters are used.
     * @param mediaType the optional MIME type for the resource (pass {@code null} if you don't care)
     * @param data the resource that should be encoded by the URL.
     * @return an URL that would yield {@code data} when resolved.
     */
    public static String createDataUrl(@Nullable String mediaType, String data) {
        StringBuilder sb = new StringBuilder("data:");
        if (mediaType!=null) sb.append(mediaType);
        sb.append(",");
        String encoded = rethrow(()->URLEncoder.encode(data, UTF8.name()));
        sb.append(encoded);
        return sb.toString();
    }

    /**
     * Factory method for Base64-encoded data-URLs from a string.
     * These have the advantages that they are more compact when representing binary data.
     * @param mediaType the optional MIME type for the resource (pass {@code null} if you don't care)
     * @param data the resource that should be encoded by the URL.
     * @return an URL that would yield {@code data} when resolved.
     */
    public static String createDataUrlBase64(@Nullable String mediaType, byte[] data) {
        StringBuilder sb = new StringBuilder("data:");
        if (mediaType!=null) sb.append(mediaType);
        sb.append(";base64");
        sb.append(",");
        String encoded = Base64.getUrlEncoder().encodeToString(data);
        sb.append(encoded);
        return sb.toString();
    }

    /**
     * <p>An URL stream handler that adds support for data URLs. Use when an API
     * demands an URL, but you just want to feed it a string or a bunch of bytes.
     * </p>
     *
     * @see <a href="https://tools.ietf.org/html/rfc2397">RFC2397</a>
     * @see <a href="https://en.wikipedia.org/wiki/Data_URI_scheme">Wikipedia: Data URI scheme</a>
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Data_URIs">MDN: Data URIs</a>
     */
    public static class DataUrl extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return new DataUrlConnection(u);
        }
    }

    static class DataUrlConnection extends URLConnection {
        private Map<String, String> headers;
        private byte[] content;
        private String contentStr;

        DataUrlConnection(URL url) { super(url); }

        @Override
        public void connect() throws IOException {
            String[] typeAndContent = url.toString().split(":", 2)[1].split(",",2);
            if (typeAndContent.length!=2) throw new IllegalArgumentException("Not a valid data URL: " + url);

            String[] mime = typeAndContent[0].split(";");
            boolean base64 = "base64".equalsIgnoreCase(mime[mime.length - 1]);
            if (base64) mime = Arrays.copyOfRange(mime, 0, mime.length - 1);

            boolean hasMediaType = mime[0].contains("/") && !mime[0].contains("=");
            // we don't use this: String mediaType= hasMediaType ? mime[0] : "text/plain";
            if (hasMediaType) mime = Arrays.copyOfRange(mime, 1, mime.length);

            Charset charset = US_ASCII;
            for (String mimeParam : mime) {
                if (mimeParam.isEmpty()) continue;

                String[] param = mimeParam.split("=", 2);
                if (param.length != 2) {
                    throw new IllegalArgumentException("Malformed parameter: " + mimeParam);
                }
                if (param[0].equalsIgnoreCase("charset")) {
                    charset = Charset.forName(param[1]);
                }
            }

            String urlDecodedContent = URLDecoder.decode(typeAndContent[1], charset.name());
            if (base64) {
                String s = urlDecodedContent.replaceAll("[\\s\\r\\n]+", "");
                content = Base64.getUrlDecoder().decode(s);
            } else {
                contentStr = urlDecodedContent;
                content = contentStr.getBytes(charset);
            }

            headers = new HashMap<>();
            headers.put("content-type", typeAndContent[0]);
            headers.put("content-length", String.valueOf(content.length));
            headers.put("content-encoding", "identity");
            headers.put("date", new Date().toString());
            headers.put("expires", new Date(Long.MAX_VALUE).toString());
            headers.put("last-modified", new Date(0).toString());

            this.connected = true;
        }

        @Override
        public String getHeaderField(String name) {
            return headers.get(name);
        }


        @Override
        public InputStream getInputStream() throws IOException {
            connect();
            return new ByteArrayInputStream(content);
        }

        @Override
        public Object getContent() throws IOException {
            connect();
            return content;
        }

        @Override @SuppressWarnings("rawtypes")
        public Object getContent(Class[] classes) throws IOException {
            connect();
            for (Class negotiatedType : classes) {
                if (negotiatedType==byte[].class) return content;
                if (negotiatedType==String.class) {
                    return fallback(false, Objects::nonNull, // return the first non-null
                            bytes -> contentStr,
                            bytes -> new String(content, Charset.defaultCharset()),
                            bytes -> new String(content, US_ASCII),
                            bytes -> new String(content, UTF8)
                    ).apply(content);
                }
            }
            return super.getContent(classes);
        }
    }

    /**
     * <p>An URL stream handler that resolves to another URL schema - use this to build
     * custom URL schemas that can shorten URIs or lookup resources.</p>
     *
     * <p>This class is abstract, so you may want to consider instead the {@link ResolversUrl},
     * which uses lambda functions to resolve the resource.</p>
     */
    public static abstract class ResolvingUrl extends URLStreamHandler {
        protected abstract URL resolve(String resourcePath);

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            String resource = u.toString().split(":", 2)[1];
            try {
                URL resolvedUrl = resolve(resource);
                if (resolvedUrl==null) {
                    throw new ProtocolException("Resource could not be located: " + resource);
                }
                return resolvedUrl.openConnection();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                ProtocolException pe = new ProtocolException("Resource could not be located: " + resource);
                pe.initCause(e);
                throw pe;
            }
        }
    }

    /**
     * <p>An URL stream handler that resolves to another URL schema - use this to build
     * custom URL schemas that can shorten URIs or lookup resources.</p>
     *
     * <p>This class accepts resolver strategies as lambdas in the constructor.
     * This is convenient when you just want to pass a method reference to a {@code getResources()}
     * method as in this example:</p>
     * <pre><code>
     * package sun.net.www.protocol.cp;
     * public class Handler extends UrlStreamHandlers.ResolversUrl {
     *    public Handler() { super(Foobar.class.getClassLoader::getResource, ClassLoader::getSystemResource); }
     * }
     * </code></pre>
     */
    public static class ResolversUrl extends ResolvingUrl {
        private final Function<String, URL> resolver;

        @SafeVarargs
        public ResolversUrl(Function<String, URL>... resolvers) {
            resolver = fallback(false, Objects::nonNull, resolvers);
        }

        @Override
        protected URL resolve(String resourcePath) {
            return resolver.apply(resourcePath);
        }
    }

}
