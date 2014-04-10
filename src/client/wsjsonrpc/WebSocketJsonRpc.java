package client.wsjsonrpc;

import com.google.gson.*;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class WebSocketJsonRpc<T extends WebSocketJsonRpcHandler> extends WebSocketServer {
    public static final int JSONRPC_PARSE_ERROR = -32700;
    public static final int JSONRPC_INVALID_REQUEST = -32600;
    public static final int JSONRPC_METHOD_NOT_FOUND = -32601;
    public static final int JSONRPC_INVALID_PARAMS = -32602;
    public static final int JSONRPC_INTERNAL_ERROR = -32603;

    private T handler;
    private Class<T> methodClass;
    private HashMap<WebSocket, HashSet<String>> subscriptions = new HashMap<>();

    public WebSocketJsonRpc(InetSocketAddress address, T handler, Class<T> cls) {
        super(address);
        this.handler = handler;
        this.methodClass = cls;
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        subscriptions.put(webSocket, new HashSet<String>());
        handler.onOpen(webSocket, clientHandshake);
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        handler.onClose(webSocket, i, s, b);
    }

    private JsonRpcResponse call(String id, String methodName, JsonArray params) {
        Method[] methods = methodClass.getMethods();

        for(Method method : methods) {
            if(method.getName().equals(methodName)) {
                System.out.println(method.getName() + ":");

                Annotation[] annotations = method.getDeclaredAnnotations();


                boolean found = false;

                for(Annotation annotation : annotations) {
                    System.out.println(" -> " + annotation.getClass().getName());

                    if(annotation instanceof Expose) {
                        found = true;
                    }
                }

                if(found) {

                    if(method.getParameterTypes().length != params.size()) {
                        return new JsonRpcError(id, JSONRPC_INVALID_PARAMS, "Invalid parameter array length");
                    }

                    Object[] callParams = new Object[params.size()];
                    Class[] classes = method.getParameterTypes();

                    for(int i = 0; i < params.size(); i++) {
                        if(classes[i] == String.class) {
                            callParams[i] = params.get(i).getAsString();
                        } else if(classes[i] == int.class) {
                            callParams[i] = params.get(i).getAsInt();
                        } else {
                            return new JsonRpcError(id, JSONRPC_INVALID_PARAMS, "Unknown parameter type: " + classes[i].getName());
                        }
                    }

                    try {
                        return new JsonRpcResult(id, method.invoke(handler, callParams));
                    } catch (IllegalAccessException e) {
                        return new JsonRpcError(id, JSONRPC_INTERNAL_ERROR, "Internal Error: IllegalAccessException");
                    } catch (InvocationTargetException e) {
                        return new JsonRpcError(id, JSONRPC_INTERNAL_ERROR, "Internal Error: InvocationTargetException");
                    }
                }
            }
        }

        return new JsonRpcError(id, JSONRPC_METHOD_NOT_FOUND, "The specified method was not found.");
    }

    private JsonRpcResponse getResponse(JsonObject object) {
        if(object.has("id") && object.has("jsonrpc") && object.has("method") && object.has("params")) {
            try {
                String id = object.get("id").getAsString();
                String version = object.get("jsonrpc").getAsString();
                String method = object.get("method").getAsString();
                JsonArray params = object.get("params").getAsJsonArray();

                if(!version.equals("2.0")) {
                    return new JsonRpcError(id, JSONRPC_INVALID_REQUEST, "Wrong jsonrpc version.");
                }

                return call(id, method, params);
            } catch(UnsupportedOperationException e) {
                return new JsonRpcError(null, JSONRPC_INVALID_REQUEST, "Request has wrong parameter types.");
            } finally {

            }

        } else {
            return new JsonRpcError(null, JSONRPC_INVALID_REQUEST, "Request is missing parameters.");
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        JsonElement element = new JsonParser().parse(s);
        Object response;

        if(element.isJsonObject()) {
            response = getResponse(element.getAsJsonObject());
        } else if(element.isJsonArray()) {
            LinkedList<JsonRpcResponse> responses = new LinkedList<>();

            for(JsonElement call : element.getAsJsonArray()) {
                if(call.isJsonObject()) {
                    responses.add(getResponse(call.getAsJsonObject()));
                } else {
                    responses.add(new JsonRpcError(null, JSONRPC_INVALID_REQUEST, "Request is not an object."));
                }
            }

            response = new JsonRpcResponse[responses.size()];
            responses.toArray((JsonRpcResponse[]) response);
        } else {
            response = new JsonRpcError(null, JSONRPC_INVALID_REQUEST, "Root element is not an array or object.");
        }

        webSocket.send(new Gson().toJson(response));
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        handler.onError(webSocket, e);
    }
}
