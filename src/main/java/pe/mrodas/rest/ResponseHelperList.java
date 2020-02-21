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

    private final ResponseHelper.Json<List<T>> responseHelper;
    private ThrowingFunction<List<T>, String> listToJson;

    public ResponseHelperList(Callable<List<T>> callable) {
        responseHelper = new ResponseHelper.Json<>(callable);
    }

    public ResponseHelperList(ThrowingFunction<Connection, List<T>> function) {
        responseHelper = new ResponseHelper.Json<>(function);
    }

    public ResponseHelperList(ThrowingBiConsumer<Connection, List<T>> biConsumer) {
        List<T> list = new ArrayList<>();
        responseHelper = new ResponseHelper.Json<>(connection -> {
            biConsumer.accept(connection, list);
            return list;
        });
    }

    public ResponseHelperList<T> setListToJson(ThrowingFunction<List<T>, String> listToJson) {
        this.listToJson = listToJson;
        return this;
    }

    public Response getResponse() {
        return this.getResponse(null);
    }

    public Response getResponse(Consumer<Exception> onException) {
        if (responseHelper.getCallable() == null)
            return responseHelper.getResponseOkBuilder(null).build();
        try {
            List<T> list = responseHelper.getCallable().call();
            return responseHelper.getResponseOkBuilder(list, listToJson).build();
        } catch (Exception e) {
            if (onException != null) onException.accept(e);
            return Response.serverError().type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();
        }
    }

}
