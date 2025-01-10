/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;

enum JavaxHttpServletRequestGetter implements ExtendedTextMapGetter<HttpServletRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpServletRequest carrier) {
    return Collections.list(carrier.getHeaderNames());
  }

  @Override
  public String get(HttpServletRequest carrier, String key) {
    return carrier.getHeader(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable HttpServletRequest carrier, String key) {
    if (carrier == null) {
      return Collections.emptyIterator();
    }

    return new Iterator<String>() {
      private final Enumeration<String> x = carrier.getHeaders(key);

      @Override
      public boolean hasNext() {
        return x.hasMoreElements();
      }

      @Override
      public String next() {
        return x.nextElement();
      }
    };
  }
}
