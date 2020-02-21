package pe.mrodas.rest;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.codehaus.jackson.map.ObjectMapper;

import pe.mrodas.jdbc.Connector;
import pe.mrodas.jdbc.helper.ThrowingConsumer;
import pe.mrodas.jdbc.helper.ThrowingFunction;
import pe.mrodas.jdbc.helper.ThrowingRunnable;

public class ResponseHelper<T> {

    private final Callable<T> callable;
    private MediaType mediaType;

    public ResponseHelper(Callable<T> callable) {
        this.callable = callable;
    }

    public ResponseHelper(ThrowingFunction<Connection, T> function) {
        this.callable = function == null ? null : () -> Connector.batch(function);
    }

    public ResponseHelper(ThrowingRunnable runnable) {
        this.callable = runnable == null ? null : () -> {
            runnable.run();
            return null;
        };
    }

    public ResponseHelper(ThrowingConsumer<Connection> consumer) {
        this.callable = consumer == null ? null : () -> {
            Connector.batch(consumer);
            return null;
        };
    }

    protected Callable<T> getCallable() {
        return callable;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public Response getResponse() {
        return this.getResponse(null);
    }

    protected Response.ResponseBuilder getResponseOkBuilder(T entity) {
        if (entity == null) return mediaType == null ? Response.ok() : Response.ok().type(mediaType);
        return mediaType == null ? Response.ok(entity) : Response.ok(entity, mediaType);
    }

    public Response getResponse(Consumer<Exception> onException) {
        if (callable == null) return this.getResponseOkBuilder(null).build();
        try {
            T entity = callable.call();
            return this.getResponseOkBuilder(entity).build();
        } catch (Exception e) {
            if (onException != null) onException.accept(e);
            return Response.serverError().type(MediaType.TEXT_PLAIN)
                    .entity(e.getMessage()).build();
        }
    }

    public static class Json<T> extends ResponseHelper<T> {

        private String dateFormat;

        public Json(Callable<T> callable) {
            super(callable);
            super.setMediaType(MediaType.APPLICATION_JSON_TYPE);
        }

        public Json(ThrowingFunction<Connection, T> function) {
            super(function);
            super.setMediaType(MediaType.APPLICATION_JSON_TYPE);
        }

        public Json(ThrowingRunnable runnable) {
            super(runnable);
            super.setMediaType(MediaType.APPLICATION_JSON_TYPE);
        }

        public Json(ThrowingConsumer<Connection> consumer) {
            super(consumer);
            super.setMediaType(MediaType.APPLICATION_JSON_TYPE);
        }

        public Json<T> setDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
            return this;
        }

        private ObjectMapper getNewObjectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            String format = dateFormat == null || dateFormat.isEmpty()
                    ? "yyyy-MM-dd'T'HH:mm:ss" : dateFormat;
            mapper.setDateFormat(new SimpleDateFormat(format));
            return mapper;
        }

        public Response.ResponseBuilder getResponseOkBuilder(T entity, ThrowingFunction<T, String> toJson) throws Exception {
            if (entity == null) return super.getResponseOkBuilder(null);
            String json = toJson == null
                    ? this.getNewObjectMapper().writeValueAsString(entity)
                    : toJson.apply(entity);
            return Response.ok(json, MediaType.APPLICATION_JSON_TYPE);
        }
    }

    public static Response toResponse(File attach) {
        return ResponseHelper.toResponse(attach, attach.getName());
    }

    public static Response toResponse(File attach, String fileName) {
        NewCookie newCookie = new NewCookie("fileDownload", "true", "/", "", "", 60, false);
        return Response.ok().header("Content-Disposition", "attachment; filename= " + fileName)
                .type(MediaType.TEXT_HTML)
                .cookie(newCookie)
                .entity(attach)
                .build();
    }

    public static Response toResponse(byte[] data, String fileName) {
        StreamingOutput streamingOutput = output -> {
            output.write(data);
            output.flush();
        };
        return Response.ok(streamingOutput, MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename= " + fileName)
                .build();
    }

    public static boolean isSuccessful(Response response) {
        if (response == null) return false;
        return Response.Status.Family.SUCCESSFUL == Response.Status.Family.familyOf(response.getStatus());
    }

    public static boolean isOk(Response response) {
        if (response == null) return false;
        return Response.Status.OK == Response.Status.fromStatusCode(response.getStatus());
    }

    public static boolean isError(Response response) {
        if (response == null) return false;
        Response.Status.Family family = Response.Status.Family.familyOf(response.getStatus());
        return Response.Status.Family.CLIENT_ERROR == family
                || Response.Status.Family.SERVER_ERROR == family;
    }

}
