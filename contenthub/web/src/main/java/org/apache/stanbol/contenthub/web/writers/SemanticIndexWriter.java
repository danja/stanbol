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
package org.apache.stanbol.contenthub.web.writers;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.IOUtils;
import org.apache.stanbol.contenthub.servicesapi.Constants;
import org.apache.stanbol.contenthub.servicesapi.index.IndexException;
import org.apache.stanbol.contenthub.servicesapi.index.SemanticIndex;
import org.apache.stanbol.contenthub.web.util.JSONUtils;
import org.apache.stanbol.contenthub.web.util.RestUtil;
import org.codehaus.jettison.json.JSONException;

/**
 * 
 * @author suat
 * 
 */
@Provider
public class SemanticIndexWriter implements MessageBodyWriter<List<SemanticIndex>> {

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        boolean isWritable = false;
        String mediaTypeString = mediaType.getType() + '/' + mediaType.getSubtype();

        if (List.class.isAssignableFrom(type) && genericType instanceof ParameterizedType
            && RestUtil.supportedMediaTypes.contains(mediaTypeString)) {

            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            Type[] actualTypeArgs = (parameterizedType.getActualTypeArguments());

            Class<?> actualType = (Class<?>) actualTypeArgs[0];
            isWritable = (actualTypeArgs.length == 1 && SemanticIndex.class.isAssignableFrom(actualType));
        }
        return isWritable;
    }

    @Override
    public long getSize(List<SemanticIndex> t,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<SemanticIndex> t,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType,
                        MultivaluedMap<String,Object> httpHeaders,
                        OutputStream entityStream) throws IOException, WebApplicationException {
        String mediaTypeString = mediaType.getType() + '/' + mediaType.getSubtype();
        String encoding = mediaType.getParameters().get("charset");
        if (encoding == null) {
            encoding = Constants.DEFAULT_ENCODING;
        }
        if (APPLICATION_JSON.equals(mediaTypeString)) {
            try {
                String jsonstr = JSONUtils.createJSONString(t);
                IOUtils.write(jsonstr, entityStream, encoding);
            } catch (JSONException e) {
                throw new WebApplicationException(e, Status.INTERNAL_SERVER_ERROR);
            } catch (IndexException e) {
                throw new WebApplicationException(e, Status.INTERNAL_SERVER_ERROR);
            }
        }
    }

}