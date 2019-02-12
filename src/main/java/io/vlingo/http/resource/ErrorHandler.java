package io.vlingo.http.resource;

import io.vlingo.common.Completes;
import io.vlingo.http.Response;

public interface ErrorHandler {
  Completes<Response> handle(Throwable error);
}
