package io.atomix.server.protocol.impl;

import java.util.concurrent.CompletableFuture;

import com.google.protobuf.Message;
import io.atomix.server.protocol.ProtocolClient;
import io.atomix.service.client.SessionClient;
import io.atomix.service.operation.CommandId;
import io.atomix.service.operation.QueryId;
import io.atomix.service.operation.StreamType;
import io.atomix.service.protocol.CloseSessionRequest;
import io.atomix.service.protocol.CloseSessionResponse;
import io.atomix.service.protocol.DeleteRequest;
import io.atomix.service.protocol.KeepAliveRequest;
import io.atomix.service.protocol.KeepAliveResponse;
import io.atomix.service.protocol.OpenSessionRequest;
import io.atomix.service.protocol.OpenSessionResponse;
import io.atomix.service.protocol.ServiceId;
import io.atomix.service.protocol.ServiceRequest;
import io.atomix.service.protocol.ServiceResponse;
import io.atomix.service.protocol.SessionCommandContext;
import io.atomix.service.protocol.SessionCommandRequest;
import io.atomix.service.protocol.SessionQueryContext;
import io.atomix.service.protocol.SessionQueryRequest;
import io.atomix.service.protocol.SessionRequest;
import io.atomix.service.protocol.SessionResponse;
import io.atomix.service.protocol.SessionResponseContext;
import io.atomix.service.protocol.SessionStreamContext;
import io.atomix.service.util.ByteArrayDecoder;
import io.atomix.service.util.ByteBufferDecoder;
import io.atomix.service.util.ByteStringEncoder;
import io.atomix.utils.stream.StreamHandler;
import org.apache.commons.lang3.tuple.Pair;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Default session client.
 */
public class DefaultSessionClient implements SessionClient {
  private final ServiceId serviceId;
  private final ProtocolClient client;

  public DefaultSessionClient(ServiceId serviceId, ProtocolClient client) {
    this.serviceId = checkNotNull(serviceId);
    this.client = checkNotNull(client);
  }

  @Override
  public String name() {
    return serviceId.getName();
  }

  @Override
  public String type() {
    return serviceId.getType();
  }

  @Override
  public CompletableFuture<OpenSessionResponse> openSession(OpenSessionRequest request) {
    return command(SessionRequest.newBuilder()
        .setOpenSession(request)
        .build())
        .thenApply(response -> response.getOpenSession());
  }

  @Override
  public CompletableFuture<KeepAliveResponse> keepAlive(KeepAliveRequest request) {
    return command(SessionRequest.newBuilder()
        .setKeepAlive(request)
        .build())
        .thenApply(response -> response.getKeepAlive());
  }

  @Override
  public CompletableFuture<CloseSessionResponse> closeSession(CloseSessionRequest request) {
    return command(SessionRequest.newBuilder()
        .setCloseSession(request)
        .build())
        .thenApply(response -> response.getCloseSession());
  }

  @Override
  public CompletableFuture<Void> delete() {
    return client.command(ServiceRequest.newBuilder()
        .setId(serviceId)
        .setDelete(DeleteRequest.newBuilder().build())
        .build()
        .toByteArray())
        .thenApply(v -> null);
  }

  @Override
  public <T extends Message, U extends Message> CompletableFuture<Pair<SessionResponseContext, U>> execute(
      CommandId<T, U> command, SessionCommandContext context, T request, ByteStringEncoder<T> encoder, ByteBufferDecoder<U> decoder) {
    return command(SessionRequest.newBuilder()
        .setCommand(SessionCommandRequest.newBuilder()
            .setContext(context)
            .setName(command.id())
            .setInput(ByteStringEncoder.encode(request, encoder))
            .build())
        .build())
        .thenApply(response -> response.getCommand())
        .thenApply(response -> Pair.of(response.getContext(), ByteBufferDecoder.decode(response.getOutput().asReadOnlyByteBuffer(), decoder)));
  }

  @Override
  public <T extends Message, U extends Message> CompletableFuture<Pair<SessionResponseContext, U>> execute(
      QueryId<T, U> query, SessionQueryContext context, T request, ByteStringEncoder<T> encoder, ByteBufferDecoder<U> decoder) {
    return query(SessionRequest.newBuilder()
        .setQuery(SessionQueryRequest.newBuilder()
            .setContext(context)
            .setName(query.id())
            .setInput(ByteStringEncoder.encode(request, encoder))
            .build())
        .build())
        .thenApply(response -> response.getQuery())
        .thenApply(response -> Pair.of(response.getContext(), ByteBufferDecoder.decode(response.getOutput().asReadOnlyByteBuffer(), decoder)));
  }

  @Override
  public <T extends Message, U extends Message> CompletableFuture<Pair<SessionResponseContext, U>> execute(
      CommandId<T, U> command,
      StreamType<U> streamType,
      SessionCommandContext context,
      T request,
      ByteStringEncoder<T> encoder,
      ByteBufferDecoder<U> decoder) {
    CompletableFuture<Pair<SessionResponseContext, U>> future = new CompletableFuture<>();
    command(
        SessionRequest.newBuilder()
            .setCommand(SessionCommandRequest.newBuilder()
                .setContext(context)
                .setName(command.id())
                .setInput(ByteStringEncoder.encode(request, encoder))
                .build())
            .build(),
        new StreamHandler<SessionResponse>() {
          private SessionResponseContext responseContext;

          @Override
          public void next(SessionResponse response) {
            if (response.hasCommand()) {
              responseContext = response.getCommand().getContext();
            } else {
              future.complete(Pair.of(responseContext, ByteBufferDecoder.decode(response.getStream().getValue().asReadOnlyByteBuffer(), decoder)));
            }
          }

          @Override
          public void complete() {
            future.completeExceptionally(new IllegalStateException());
          }

          @Override
          public void error(Throwable error) {
            future.completeExceptionally(error);
          }
        })
        .whenComplete((result, error) -> {
          if (error != null) {
            future.completeExceptionally(error);
          }
        });
    return future;
  }

  @Override
  public <T extends Message, U extends Message> CompletableFuture<Pair<SessionResponseContext, U>> execute(
      QueryId<T, U> query,
      StreamType<U> streamType,
      SessionQueryContext context,
      T request,
      ByteStringEncoder<T> encoder,
      ByteBufferDecoder<U> decoder) {
    CompletableFuture<Pair<SessionResponseContext, U>> future = new CompletableFuture<>();
    query(
        SessionRequest.newBuilder()
            .setQuery(SessionQueryRequest.newBuilder()
                .setContext(context)
                .setName(query.id())
                .setInput(ByteStringEncoder.encode(request, encoder))
                .build())
            .build(),
        new StreamHandler<SessionResponse>() {
          private SessionResponseContext responseContext;

          @Override
          public void next(SessionResponse response) {
            if (response.hasQuery()) {
              responseContext = response.getQuery().getContext();
            } else {
              future.complete(Pair.of(responseContext, ByteBufferDecoder.decode(response.getStream().getValue().asReadOnlyByteBuffer(), decoder)));
            }
          }

          @Override
          public void complete() {
            future.completeExceptionally(new IllegalStateException());
          }

          @Override
          public void error(Throwable error) {
            future.completeExceptionally(error);
          }
        })
        .whenComplete((result, error) -> {
          if (error != null) {
            future.completeExceptionally(error);
          }
        });
    return future;
  }

  @Override
  public <T extends Message, U extends Message> CompletableFuture<SessionResponseContext> execute(
      CommandId<T, U> command,
      StreamType<U> streamType,
      SessionCommandContext context,
      T request,
      StreamHandler<Pair<SessionStreamContext, U>> handler,
      ByteStringEncoder<T> encoder,
      ByteBufferDecoder<U> decoder) {
    CompletableFuture<SessionResponseContext> future = new CompletableFuture<>();
    command(
        SessionRequest.newBuilder()
            .setCommand(SessionCommandRequest.newBuilder()
                .setContext(context)
                .setName(command.id())
                .setInput(ByteStringEncoder.encode(request, encoder))
                .build())
            .build(),
        new StreamHandler<SessionResponse>() {
          @Override
          public void next(SessionResponse response) {
            if (response.hasCommand()) {
              future.complete(response.getCommand().getContext());
            } else {
              handler.next(Pair.of(
                  response.getStream().getContext(),
                  ByteBufferDecoder.decode(response.getStream().getValue().asReadOnlyByteBuffer(), decoder)));
            }
          }

          @Override
          public void complete() {
            handler.complete();
          }

          @Override
          public void error(Throwable error) {
            handler.error(error);
          }
        })
        .whenComplete((result, error) -> {
          if (error != null) {
            future.completeExceptionally(error);
          }
        });
    return future;
  }

  @Override
  public <T extends Message, U extends Message> CompletableFuture<SessionResponseContext> execute(
      QueryId<T, U> query,
      StreamType<U> streamType,
      SessionQueryContext context,
      T request,
      StreamHandler<Pair<SessionStreamContext, U>> handler,
      ByteStringEncoder<T> encoder,
      ByteBufferDecoder<U> decoder) {
    CompletableFuture<SessionResponseContext> future = new CompletableFuture<>();
    query(SessionRequest.newBuilder()
            .setQuery(SessionQueryRequest.newBuilder()
                .setContext(context)
                .setName(query.id())
                .setInput(ByteStringEncoder.encode(request, encoder))
                .build())
            .build(),
        new StreamHandler<SessionResponse>() {
          @Override
          public void next(SessionResponse response) {
            if (response.hasQuery()) {
              future.complete(response.getQuery().getContext());
            } else {
              handler.next(Pair.of(
                  response.getStream().getContext(),
                  ByteBufferDecoder.decode(response.getStream().getValue().asReadOnlyByteBuffer(), decoder)));
            }
          }

          @Override
          public void complete() {
            handler.complete();
          }

          @Override
          public void error(Throwable error) {
            handler.error(error);
          }
        })
        .whenComplete((result, error) -> {
          if (error != null) {
            future.completeExceptionally(error);
          }
        });
    return future;
  }

  private CompletableFuture<SessionResponse> command(SessionRequest request) {
    return client.command(ServiceRequest.newBuilder()
        .setId(serviceId)
        .setCommand(request.toByteString())
        .build()
        .toByteArray())
        .thenApply(response -> ByteArrayDecoder.decode(response, ServiceResponse::parseFrom))
        .thenApply(response -> ByteBufferDecoder.decode(response.getCommand().asReadOnlyByteBuffer(), SessionResponse::parseFrom));
  }

  private CompletableFuture<Void> command(SessionRequest request, StreamHandler<SessionResponse> handler) {
    return client.command(ServiceRequest.newBuilder()
            .setId(serviceId)
            .setCommand(request.toByteString())
            .build()
            .toByteArray(),
        new StreamHandler<byte[]>() {
          @Override
          public void next(byte[] response) {
            ServiceResponse serviceResponse = ByteArrayDecoder.decode(response, ServiceResponse::parseFrom);
            SessionResponse sessionResponse = ByteBufferDecoder.decode(serviceResponse.getCommand().asReadOnlyByteBuffer(), SessionResponse::parseFrom);
            handler.next(sessionResponse);
          }

          @Override
          public void complete() {
            handler.complete();
          }

          @Override
          public void error(Throwable error) {
            handler.error(error);
          }
        });
  }

  private CompletableFuture<SessionResponse> query(SessionRequest request) {
    return client.query(ServiceRequest.newBuilder()
        .setId(serviceId)
        .setQuery(request.toByteString())
        .build()
        .toByteArray())
        .thenApply(response -> ByteArrayDecoder.decode(response, ServiceResponse::parseFrom))
        .thenApply(response -> ByteBufferDecoder.decode(response.getQuery().asReadOnlyByteBuffer(), SessionResponse::parseFrom));
  }

  private CompletableFuture<Void> query(SessionRequest request, StreamHandler<SessionResponse> handler) {
    return client.query(ServiceRequest.newBuilder()
            .setId(serviceId)
            .setQuery(request.toByteString())
            .build()
            .toByteArray(),
        new StreamHandler<byte[]>() {
          @Override
          public void next(byte[] response) {
            ServiceResponse serviceResponse = ByteArrayDecoder.decode(response, ServiceResponse::parseFrom);
            SessionResponse sessionResponse = ByteBufferDecoder.decode(serviceResponse.getQuery().asReadOnlyByteBuffer(), SessionResponse::parseFrom);
            handler.next(sessionResponse);
          }

          @Override
          public void complete() {
            handler.complete();
          }

          @Override
          public void error(Throwable error) {
            handler.error(error);
          }
        });
  }
}