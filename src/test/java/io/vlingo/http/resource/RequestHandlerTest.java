package io.vlingo.http.resource;

import io.vlingo.common.Completes;
import io.vlingo.http.Method;
import io.vlingo.http.Request;
import io.vlingo.http.Response;
import io.vlingo.http.sample.user.NameData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.vlingo.http.Response.Status.InternalServerError;
import static org.junit.Assert.assertEquals;

public class RequestHandlerTest extends RequestHandlerTestBase {

  @Test
  public void executionErrorUsesErrorHandlerWhenExceptionThrown() {
  }

  @Test
  public void internalErrorReturnedWhenErrorHandlerFails() {
    final RequestHandlerFake handler = new RequestHandlerFake(Method.GET, "/hello", new ArrayList<>());

    ErrorHandler badHandler = (ex) -> {
      throw new IllegalArgumentException("foo");
    };

    Response response = handler.execute(badHandler).await();
    assertResponsesAreEquals(Response.of(InternalServerError),response);
  }

  @Test
  public void internalErrorReturnedWhenNoErrorHandlerDefined() {
  }


  @Test
  public void generateActionSignatureWhenNoPathIsSpecifiedIsEmptyString() {
    final RequestHandlerFake handler = new RequestHandlerFake(
      Method.GET,
      "/hello",
      Collections.singletonList(ParameterResolver.body(NameData.class)));

    assertEquals("", handler.actionSignature);
  }

  @Test
  public void generateActionSignatureWithOnePathParameterReturnsSignatureWithOneParam() {
    final RequestHandlerFake handler = new RequestHandlerFake(
      Method.GET,
      "/user/{userId}",
      Collections.singletonList(ParameterResolver.path(0, String.class)));

    assertEquals("String userId", handler.actionSignature);
  }

  @Test
  public void generateActionWithTwoPathParameters() {
    final RequestHandlerFake handler = new RequestHandlerFake(
      Method.GET,
      "/user/{userId}/comment/{commentId}",
      Arrays.asList(ParameterResolver.path(0, String.class), ParameterResolver.path(0, Integer.class)));

    assertEquals("String userId, Integer commentId", handler.actionSignature);
  }

  @Test
  public void generateActionWithOnePathParameterAndBodyJustReturnPathParameterSignature() {
    final RequestHandlerFake handler = new RequestHandlerFake(
      Method.GET,
      "/user/{userId}",
      Arrays.asList(ParameterResolver.path(0, String.class), ParameterResolver.body(NameData.class)));

    assertEquals("String userId", handler.actionSignature);
  }

  @Test(expected = IllegalArgumentException.class)
  public void unsortedPathParametersThrowsException() {
    new RequestHandlerFake(
      Method.GET,
      "/user/{userId}",
      Arrays.asList(ParameterResolver.body(NameData.class), ParameterResolver.path(0, String.class)));
  }
}

class RequestHandlerFake extends RequestHandler {

  RequestHandlerFake(Method method, String path, List<ParameterResolver<?>> parameterResolvers) {
    super(method, path, parameterResolvers);
  }

  @Override
  Completes<Response> execute(final Request request, final Action.MappedParameters mappedParameters) {
    throw new UnsupportedOperationException();
  }

  Completes<Response> execute(ErrorHandler errorHandler) {
    return executeRequest(() -> Completes.withSuccess(Response.of(Response.Status.Ok)), errorHandler);
  }
}
