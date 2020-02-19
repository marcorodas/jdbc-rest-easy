package pe.mrodas.rest;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import pe.mrodas.jdbc.helper.ThrowingBiConsumer;
import pe.mrodas.jdbc.helper.ThrowingFunction;

public class ResponseHelperList<T> {
    private final ResponseHelper<List<T>> responseHelper;
    private ThrowingFunction<List<T>, String> listToJson;

    public ResponseHelperList(Callable<List<T>> callable) {
        responseHelper = new ResponseHelper<>(callable);
    }

    public ResponseHelperList(ThrowingBiConsumer<Connection, List<T>> biConsumer) {
        List<T> list = new ArrayList<>();
        responseHelper = new ResponseHelper<>(connection -> {
            biConsumer.accept(connection, list);
            return list;
        });
    }

    public ResponseHelperList(ThrowingFunction<Connection, List<T>> function) {
        responseHelper = new ResponseHelper<>(function);
    }

    public ResponseHelperList<T> setListToJson(ThrowingFunction<List<T>, String> listToJson) {
        this.listToJson = listToJson;
        return this;
    }

    public Response getResponse() {
        return this.getResponse(null, null);
    }

    public Response getResponse(MediaType type) {
        return this.getResponse(type, null);
    }

    public Response getResponse(Consumer<Exception> onException) {
        return this.getResponse(null, onException);
    }

    public Response getResponse(MediaType type, Consumer<Exception> onException) {
        if (responseHelper.getCallable() == null) return ResponseHelper.getResponseOk(type, null);
        try {
            List<T> list = responseHelper.getCallable().call();
            if (list == null) return ResponseHelper.getResponseOk(type, null);
            String entity = listToJson == null
                    ? ResponseHelper.objtoJson(list) : listToJson.apply(list);
            return ResponseHelper.getResponseOk(type, entity);
        } catch (Exception e) {
            if (onException != null) onException.accept(e);
            return Response.serverError().type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();
        }
    }

}
