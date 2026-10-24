package com.wl.orders;

import android.content.Context;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class OrdersManager {

    // Passed to ApiCallback.onError when the server says the login token is no
    // longer valid (expired, or the admin password was changed).
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";

    // Bumped whenever an order is changed from the details screen. Home and the Orders
    // list compare it with the value they last loaded and refresh themselves in onResume.
    public static int changeVersion = 0;

    private final Context context;
    private final String token;

    public OrdersManager(Context context, String token) {
        this.context = context;
        this.token = token;
    }

    // Every order from every customer, with line items included.
    public void getOrders(ApiCallback callback) {
        send(Request.Method.GET, ApiConfig.GET_ORDERS_URL, null, callback);
    }

    public void getOrderDetail(int orderId, ApiCallback callback) {
        send(Request.Method.GET, ApiConfig.GET_ORDER_DETAIL_URL + "?order_id=" + orderId, null, callback);
    }

    // Newest published products (the Products section on Home). limit <= 0 = all of them.
    public void getProducts(int limit, ApiCallback callback) {
        String url = ApiConfig.GET_PRODUCTS_URL + (limit > 0 ? "?limit=" + limit : "");
        send(Request.Method.GET, url, null, callback);
    }

    // Moves an order one step forward (pending/paid -> processing -> preparing -> shipped -> delivered).
    public void updateStatus(int orderId, String newStatus, ApiCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("order_id", orderId);
            body.put("status", newStatus);
        } catch (JSONException e) {
            callback.onError("Bad request");
            return;
        }
        send(Request.Method.POST, ApiConfig.UPDATE_ORDER_STATUS_URL, body, callback);
    }

    private void send(int method, String url, JSONObject body, ApiCallback callback) {
        JsonObjectRequest request = new JsonObjectRequest(
                method, url, body,
                callback::onSuccess,
                error -> callback.onError(parseError(error))) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("X-Auth-Token", token);
                return headers;
            }
        };
        request.setShouldCache(false); // always show fresh orders
        request.setRetryPolicy(new DefaultRetryPolicy(20000, 1, 1f));
        VolleySingleton.getInstance(context).addToRequestQueue(request);
    }

    private String parseError(VolleyError error) {
        if (error.networkResponse != null) {
            if (error.networkResponse.statusCode == 401) {
                return SESSION_EXPIRED;
            }
            if (error.networkResponse.data != null) {
                return new String(error.networkResponse.data);
            }
        }
        return error.getMessage() != null ? error.getMessage() : "Network error";
    }
}
