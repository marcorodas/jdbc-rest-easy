package pe.mrodas.rest;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.codehaus.jackson.map.ObjectMapper;

import pe.mrodas.jdbc.Connector;
import pe.mrodas.jdbc.helper.ThrowingConsumer;
import pe.mrodas.jdbc.helper.ThrowingFunction;

public class ResponseHelper<T> {

    private Callable<T> callable;
    private Callable<List<T>> callableList;
    private ThrowingFunction<List<T>, String> listToJson;

    public ResponseHelper<T> set(ThrowingFunction<Connection, T> function) {
        return this.set(() -> Connector.batch(function));
    }

    public ResponseHelper<T> set(ThrowingConsumer<Connection> consumer) {
        return this.set(() -> {
            Connector.batch(consumer);
            return null;
        });
    }

    public ResponseHelper<T> set(Callable<T> callable) {
        this.callable = callable;
        this.callableList = null;
        return this;
    }

    public ResponseHelper<T> setForList(ThrowingFunction<Connection, List<T>> function) {
        return this.setForList(() -> Connector.batch(function), null);
    }

    public ResponseHelper<T> setForList(ThrowingFunction<Connection, List<T>> function, ThrowingFunction<List<T>, String> listToJson) {
        return this.setForList(() -> Connector.batch(function), listToJson);
    }

    public ResponseHelper<T> setForList(Callable<List<T>> callableList) {
        return this.setForList(callableList, null);
    }

    public ResponseHelper<T> setForList(Callable<List<T>> callableList, ThrowingFunction<List<T>, String> listToJson) {
        this.callable = null;
        this.callableList = callableList;
        this.listToJson = listToJson == null ? ResponseHelper::toJsonString : listToJson;
        return this;
    }

    public Response getResponse() {
        return this.getResponse(null);
    }

    public Response getResponse(Consumer<Exception> onException) {
        try {
            if (callable != null) {
                T entity = callable.call();
                return entity == null ? Response.ok().build() : Response.ok(entity).build();
            }
            if (callableList != null) {
                List<T> list = callableList.call();
                if (list == null) return Response.ok().build();
                String entity = listToJson.apply(list);
                return Response.ok(entity).build();
            }
        } catch (Exception e) {
            if (onException != null) onException.accept(e);
            return Response.serverError().type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();
        }
        return Response.ok().build();
    }

    private static String toJsonString(Object object) throws IOException {
        return ResponseHelper.toJsonString(object, null);
    }

    private static String toJsonString(Object object, String dateFormat) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        SimpleDateFormat format = dateFormat == null || dateFormat.isEmpty()
                ? new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                : new SimpleDateFormat(dateFormat);
        mapper.setDateFormat(format);
        return mapper.writeValueAsString(object);
    }

    public static Response toResponse(File attach) {
        return toResponse(attach, attach.getName());
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
