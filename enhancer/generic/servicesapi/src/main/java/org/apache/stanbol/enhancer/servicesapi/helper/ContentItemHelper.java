/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.stanbol.enhancer.servicesapi.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.clerezza.rdf.core.UriRef;
import org.apache.commons.io.IOUtils;
import org.apache.stanbol.enhancer.servicesapi.Blob;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.NoSuchPartException;


/**
 * Helper class to factorize common code for ContentItem handling.
 *
 * @author ogrisel
 */
public class ContentItemHelper {

    public static final String DEFAULT_CONTENT_ITEM_PREFIX = "urn:content-item-";

    public static final String SHA1 = "SHA1";

    public static final int MIN_BUF_SIZE = 8 * 1024; // 8 kB

    public static final int MAX_BUF_SIZE = 64 * 1024; // 64 kB

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    
    public static final String UTF8 = "UTF-8";

    // TODO: instead of using a static helper, build an OSGi component with a
    // configurable site-wide URI namespace for ids that are local to the
    // server.

    /**
     * Check that ContentItem#getId returns a valid URI or make an urn out of
     * it.
     */
    public static UriRef ensureUri(ContentItem ci) {
        String uri = ci.getUri().getUnicodeString();
        if (!uri.startsWith("http://") && !uri.startsWith("urn:")) {
            uri = "urn:" + urlEncode(uri);
        }
        return new UriRef(uri);
    }

    public static String urlEncode(String uriPart) {
        try {
            return URLEncoder.encode(uriPart, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // will never happen since every unicode symbol can be encoded
            // to UTF-8
            return null;
        }
    }

    /**
     * Pass the binary content from in to out (if not null) while computing the
     * digest. Digest can typically be used to build ContentItem ids that map
     * the binary content of the array.
     *
     * @param in stream to read the data from
     * @param out optional output stream to
     * @param digestAlgorithm MD5 or SHA1 for instance
     * @return an hexadecimal representation of the digest
     * @throws IOException
     */
    public static String streamDigest(InputStream in, OutputStream out,
            String digestAlgorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(digestAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw (IOException) new IOException().initCause(e);
        }

        int size = in.available();
        if (size == 0) {
            size = MAX_BUF_SIZE;
        } else if (size < MIN_BUF_SIZE) {
            size = MIN_BUF_SIZE;
        } else if (size > MAX_BUF_SIZE) {
            size = MAX_BUF_SIZE;
        }
        byte[] buf = new byte[size];

        /*
         * Copy and digest.
         */
        int n;
        while ((n = in.read(buf)) != -1) {
            if (out != null) {
                out.write(buf, 0, n);
            }
            digest.update(buf, 0, n);
        }
        if (out != null) {
            out.flush();
        }
        return toHexString(digest.digest());
    }

    public static String toHexString(byte[] data) {
        StringBuilder buf = new StringBuilder(2 * data.length);
        for (byte b : data) {
            buf.append(HEX_DIGITS[(0xF0 & b) >> 4]);
            buf.append(HEX_DIGITS[0x0F & b]);
        }
        return buf.toString();
    }

    public static UriRef makeDefaultUrn(Blob blob) {
        return makeDefaultUri(DEFAULT_CONTENT_ITEM_PREFIX, blob.getStream());
    }
    public static UriRef makeDefaultUrn(InputStream in) {
        return makeDefaultUri(DEFAULT_CONTENT_ITEM_PREFIX, in);
    }
    public static UriRef makeDefaultUrn(byte[] data){
        return makeDefaultUri(DEFAULT_CONTENT_ITEM_PREFIX, new ByteArrayInputStream(data));
    }
    public static UriRef makeDefaultUri(String baseUri, Blob blob) {
        return makeDefaultUri(baseUri, blob.getStream());
    }
    public static UriRef makeDefaultUri(String baseUri, byte[] data) {
        return makeDefaultUri(baseUri, new ByteArrayInputStream(data));
    }
    public static UriRef makeDefaultUri(String baseUri, InputStream in) {
        // calculate an ID based on the digest of the content
        if (!baseUri.startsWith("urn:") && !baseUri.endsWith("/")) {
            baseUri += "/";
        }
        String hexDigest;
        try {
            hexDigest = streamDigest(in, null, SHA1);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read content for calculating" +
            		"the hexDigest of the parsed content as used for the default URI" +
            		"of an ContentItem!",e);
        }
        IOUtils.closeQuietly(in);
        return new UriRef(baseUri + SHA1.toLowerCase() + "-" + hexDigest);
    }
    /**
     * This parses and validates the mime-type and parameters from the
     * parsed mimetype string based on the definition as defined in
     * <a href="http://www.ietf.org/rfc/rfc2046.txt">rfc2046</a>. 
     * <p>
     * The mime-type is stored as value for the <code>null</code>
     * key. Parameter keys are converted to lower case. Values are stored as
     * defined in the parsed media type. Parameters with empty key, empty or no
     * values are ignored.
     * @param mimeTypeString the media type formatted as defined by 
     * <a href="http://www.ietf.org/rfc/rfc2046.txt">rfc2046</a>
     * @return A map containing the mime-type under the <code>null</code> key and 
     * all parameters with lower case keys and values.
     * @throws IllegalArgumentException if the parsed mimeTypeString is
     * <code>null</code>, empty or the parsed mime-type is empty, does not define
     * non empty '{type}/{sub-type}' or uses a wildcard for the type or sub-type.
     */
    public static Map<String,String> parseMimeType(String mimeTypeString){
        String mimeType;
        if(mimeTypeString == null || mimeTypeString.isEmpty()){
            throw new IllegalArgumentException("The parsed mime-type MUST NOT be NULL nor empty!");
        }
        Map<String,String> parsed = new HashMap<String,String>();
        StringTokenizer tokens = new StringTokenizer(mimeTypeString, ";");
        mimeType = tokens.nextToken(); //the first token is the mimeType
        if(mimeType.isEmpty()){
            throw new IllegalArgumentException("Parsed mime-type MUST NOT be empty" +
                    "(mimeType='"+mimeType+"')!");
        }
        if(mimeType.indexOf('*')>=0){
            throw new IllegalArgumentException("Parsed mime-type MUST NOT use" +
                    "Wildcards (mimeType='"+mimeType+"')!");
        }
        String[] typeSubType = mimeType.split("/");
        if(typeSubType.length != 2 || typeSubType[0].isEmpty() || typeSubType[1].isEmpty()) {
            throw new IllegalArgumentException("Parsed mime-type MUST define '{type}/{sub-type}'" +
            		"and both MUST NOT be empty(mimeType='"+mimeType+"')!");
        }
        parsed.put(null, mimeType);
        while(tokens.hasMoreTokens()){ //parse the parameters (if any)
            String parameter = tokens.nextToken();
            //check if the parameter is valid formated and has a non empty value
            int nameValueSeparator = parameter.indexOf('=');
            if(nameValueSeparator>0 && parameter.length() > nameValueSeparator+2){
                //keys are case insensitive (we use lower case)
                String key = parameter.substring(0,nameValueSeparator).toLowerCase().trim();
                if(!parsed.containsKey(key)){ //do not override existing keys
                    parsed.put(key,parameter.substring(nameValueSeparator+1).trim());
                }
            }
        }
        return parsed;
    }
    /**
     * Searches an {@link ContentItem#getPart(UriRef, Class) content part}
     * of the type {@link Blob} with one of the the parsed mimeTypes. <p>
     * NOTE:<ul>
     * <li> MimeTypes are converted to lower case before compared with
     * the entries of the parsed set. Therefore it is important that the parsed
     * set only contains lower case values!
     * <li> A read lock on the parsed {@link ContentItem} is applied while
     * searching for a fitting {@link Blob}
     * </ul><p>
     * In contrast to the contentPart related methods of the {@link ContentItem}
     * this method does NOT throw {@link NoSuchPartException}.
     * @param ci the contentITem
     * @param mimeTypes List of possible mimeTypes
     * @return the {@link UriRef URI} and the {@link Blob content} of the content 
     * part or <code>null</code> if not found
     * @throws IllegalArgumentException If the parsed {@link ContentItem} is
     * <code>null</code> or the parsed Set with the mimeTypes is <code>null</code>
     * or {@link Set#isEmpty() empty}.
     */
    public static Entry<UriRef, Blob> getBlob(ContentItem ci, Set<String> mimeTypes){
        if(ci == null){
            throw new IllegalArgumentException("The parsed ContentItem MUST NOT be NULL!");
        }
        if(mimeTypes == null || mimeTypes.isEmpty()){
            throw new IllegalArgumentException("The parsed Set with mime type  MUST NOT be NULL nor empty!");
        }
        UriRef cpUri = null;
        int index = 0;
        ci.getLock().readLock().lock();
        try {
            do {
                try {
                    cpUri = ci.getPartUri(index);
                    index++;
                    try {
                        Blob blob = ci.getPart(cpUri, Blob.class);
                        if(mimeTypes.contains(blob.getMimeType().toLowerCase())){
                            return Collections.singletonMap(cpUri, blob)
                                    .entrySet().iterator().next();
                        } // else no match
                    } catch (ClassCastException e) {
                        // not a Blob -> ignore!
                    }
                } catch (NoSuchPartException e) {
                    cpUri = null; // no more parts
                }
            } while(cpUri != null);
        } finally {
            ci.getLock().readLock().unlock();
        }
        return null; // not found
    }
    /**
     * Returns a Map with the current content parts of the parsed type. future 
     * changes to the contentParts of the content item will NOT be reflected
     * within the returned map. The ordering of the {@link Iterator}s over the 
     * returned map is consistent with the ordering of the contentPart within the
     * {@link ContentItem}. <p> When parsing {@link Object} as class the number
     * of the element will be equals to the index of that content part.<p>
     * In contrast to the contentPart related methods of the {@link ContentItem}
     * this method does NOT throw {@link NoSuchPartException}.
     * @param ci the content item
     * @param clazz the class of the content part
     * @return the Map with the {@link UriRef id}s and the content as entries.
     */
    public static <T> LinkedHashMap<UriRef,T> getContentParts(ContentItem ci, Class<T> clazz){
        if(ci == null){
            throw new IllegalArgumentException("The parsed ContentItem MUST NOT be NULL!");
        }
        LinkedHashMap<UriRef,T> blobs = new LinkedHashMap<UriRef,T>();
        UriRef cpUri = null;
        int index = 0;
        ci.getLock().readLock().lock();
        try {
            do {
                try {
                    cpUri = ci.getPartUri(index);
                    index++;
                    try {
                        blobs.put(cpUri, ci.getPart(cpUri, clazz));
                    } catch (ClassCastException e) {
                        //not of type T -> skip
                    }
                } catch (NoSuchPartException e) {
                    cpUri = null; // no more parts
                }
            } while(cpUri != null);
        } finally {
            ci.getLock().readLock().unlock();
        }        
        return blobs;
    }

    /**
     * Getter for the Text of an {@link Blob}. This method respects the
     * "charset" if present in the {@link Blob#getParameter() parameter} of the
     * Blob.
     * @param blob the {@link Blob}. MUST NOT be <code>null</code>.
     * @return the text
     * @throws IOException on any exception while reading from the
     * {@link InputStream} provided by the Blob.
     * @throws IllegalArgumentException if the parsed Blob is <code>null</code>
     */
    public static String getText(Blob blob) throws IOException {
        if(blob == null){
            throw new IllegalArgumentException("The parsed Blob MUST NOT be NULL!");
        }
        String charset = blob.getParameter().get("charset");
        return IOUtils.toString(blob.getStream(), charset != null ? charset : UTF8);
    }
    /**
     * Creates the "{type}/{subtime}; [{param}={value}]+" mime type representation
     * for the {@link Blob#getMimeType()} and {@link Blob#getParameter()} values
     * @param blob the Blob
     * @return the mime type with parameters (e.g. <code>
     * text/plain;charset=UTF-8</code>)
     */
    public static String getMimeTypeWithParameters(Blob blob) {
        StringBuilder mimeType = new StringBuilder(blob.getMimeType());
        //ensure parameters are preserved
        for(Entry<String,String> param : blob.getParameter().entrySet()){
           mimeType.append("; ").append(param.getKey()).append('=').append(param.getValue()); 
        }
        return mimeType.toString();
    }

}
