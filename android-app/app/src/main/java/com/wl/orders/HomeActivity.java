package com.wl.orders;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.wl.orders.models.Order;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// Dashboard. "Latest orders" (get_orders.php) and "Products" (get_products.php) are live;
// header, the Store Sales / Networking boxes, the Categories popup and bottom nav
// are static for now.
public class HomeActivity extends AppCompatActivity {

    private static final int MAX_CARDS = 6;    // how many latest orders to show on Home
    private static final int MAX_PRODUCTS = 6; // how many products to show in the Products section

    private SwipeRefreshLayout swipeRefresh;
    private TextView ordersMessage;
    private RecyclerView ordersRecycler;
    private HomeOrderAdapter orderAdapter;
    private HomeProductAdapter productAdapter;
    private TextView productsMessage;
    private OrdersManager ordersManager;
    private SessionManager sessionManager;
    private int seenVersion;
    private int selectedCategory = 0; // index into HomeCategoryAdapter.DEFAULT_CATEGORIES (Handsfree)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        swipeRefresh = findViewById(R.id.homeSwipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.home_blue);
        ordersMessage = findViewById(R.id.ordersMessage);
        ordersRecycler = findViewById(R.id.ordersRecycler);

        // Latest orders (dynamic)
        orderAdapter = new HomeOrderAdapter(order -> {
            Intent intent = new Intent(this, OrderDetailActivity.class);
            intent.putExtra("order_id", order.id);
            startActivity(intent);
        });
        ordersRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        ordersRecycler.setAdapter(orderAdapter);

        // Products (dynamic, from get_products.php)
        RecyclerView productsRecycler = findViewById(R.id.productsRecycler);
        productsMessage = findViewById(R.id.productsMessage);
        productAdapter = new HomeProductAdapter();
        productsRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        productsRecycler.setAdapter(productAdapter);

        // Categories icon next to "Products" -> bottom-sheet popup
        findViewById(R.id.categoriesButton).setOnClickListener(v -> showCategories());

        // Quick-access boxes. "Orders" opens the full orders list. Store Sales and Networking
        // have no screens yet: replace comingSoon(...) with
        // startActivity(new Intent(this, <YourActivity>.class)) once each one exists.
        findViewById(R.id.boxStoreSales).setOnClickListener(v -> comingSoon("Store Sales"));
        findViewById(R.id.boxNetworking).setOnClickListener(v -> comingSoon("Networking"));
        findViewById(R.id.boxOrders).setOnClickListener(v ->
                startActivity(new Intent(this, OrdersActivity.class)));

        // Top-left grid icon: small menu so Refresh / Log out stay reachable from Home
        findViewById(R.id.menuButton).setOnClickListener(this::showMenu);

        // Tap the empty/error messages to retry
        ordersMessage.setOnClickListener(v -> loadOrders());
        productsMessage.setOnClickListener(v -> loadProducts());

        ordersManager = new OrdersManager(this, sessionManager.getToken());
        swipeRefresh.setOnRefreshListener(this::refreshAll);

        refreshAll();
    }

    private void refreshAll() {
        loadOrders();
        loadProducts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // An order's status was changed on the details screen -> reload the cards.
        if (ordersManager != null && seenVersion != OrdersManager.changeVersion) {
            loadOrders();
        }
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.orders_menu, menu.getMenu()); // Refresh + Log out
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                refreshAll();
                return true;
            }
            if (id == R.id.action_logout) {
                sessionManager.clear();
                goToLogin();
                return true;
            }
            return false;
        });
        menu.show();
    }

    // Categories popup: a bottom sheet with a 3-column grid. The picked category is remembered
    // in selectedCategory so it is still highlighted next time. Products aren't filtered yet.
    private void showCategories() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.ThemeOverlay_WLOrders_BottomSheetDialog);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_categories, null);

        RecyclerView grid = sheet.findViewById(R.id.categoriesRecycler);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setAdapter(new HomeCategoryAdapter(HomeCategoryAdapter.DEFAULT_CATEGORIES, selectedCategory,
                (position, category) -> selectedCategory = position));

        sheet.findViewById(R.id.categoriesClose).setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheet);
        // Open fully expanded so every category is visible straight away.
        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(container);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    private void comingSoon(String name) {
        Toast.makeText(this, name + " is coming soon", Toast.LENGTH_SHORT).show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadOrders() {
        seenVersion = OrdersManager.changeVersion;
        swipeRefresh.setRefreshing(true);
        ordersManager.getOrders(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                swipeRefresh.setRefreshing(false);
                try {
                    if (!response.getBoolean("success")) {
                        showMessage(response.optString("message", "Couldn't load orders"));
                        return;
                    }
                    JSONArray array = response.getJSONArray("orders"); // already newest first
                    List<Order> latest = new ArrayList<>();
                    for (int i = 0; i < array.length() && i < MAX_CARDS; i++) {
                        latest.add(parseOrder(array.getJSONObject(i)));
                    }
                    if (latest.isEmpty()) {
                        showMessage("No orders yet");
                    } else {
                        orderAdapter.setOrders(latest);
                        ordersMessage.setVisibility(View.GONE);
                        ordersRecycler.setVisibility(View.VISIBLE);
                        ordersRecycler.scrollToPosition(0);
                    }
                } catch (JSONException e) {
                    showMessage("Unexpected response from server");
                }
            }

            @Override
            public void onError(String message) {
                swipeRefresh.setRefreshing(false);
                if (OrdersManager.SESSION_EXPIRED.equals(message)) {
                    sessionManager.clear();
                    Toast.makeText(HomeActivity.this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
                    goToLogin();
                    return;
                }
                showMessage("Couldn't reach server. Tap to retry.");
            }
        });
    }

    private void loadProducts() {
        ordersManager.getProducts(MAX_PRODUCTS, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (!response.getBoolean("success")) {
                        showProductsMessage(response.optString("message", "Couldn't load products"));
                        return;
                    }
                    List<HomeProductAdapter.Tile> tiles =
                            HomeProductAdapter.tilesFromJson(response.getJSONArray("products"), MAX_PRODUCTS);
                    productAdapter.setTiles(tiles);
                    if (tiles.isEmpty()) {
                        showProductsMessage("No products yet");
                    } else {
                        productsMessage.setVisibility(View.GONE);
                    }
                } catch (JSONException e) {
                    showProductsMessage("Unexpected response from server");
                }
            }

            @Override
            public void onError(String message) {
                if (OrdersManager.SESSION_EXPIRED.equals(message)) {
                    return; // loadOrders() already sends the user back to the login screen
                }
                showProductsMessage("Couldn't load products. Tap to retry.");
            }
        });
    }

    private void showProductsMessage(String text) {
        productAdapter.setTiles(new ArrayList<>());
        productsMessage.setText(text);
        productsMessage.setVisibility(View.VISIBLE);
    }

    private void showMessage(String text) {
        ordersRecycler.setVisibility(View.INVISIBLE);
        ordersMessage.setText(text);
        ordersMessage.setVisibility(View.VISIBLE);
    }

    private Order parseOrder(JSONObject o) throws JSONException {
        Order order = new Order();
        order.id = o.getInt("id");
        order.orderNumber = Format.str(o, "order_number");
        order.status = Format.str(o, "status");
        order.grandTotal = Format.str(o, "grand_total");
        order.recipientName = Format.str(o, "recipient_name");
        order.createdAt = Format.str(o, "created_at");
        order.email = Format.str(o, "email");

        int count = 0;
        JSONArray items = o.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                count += Math.max(1, items.getJSONObject(i).optInt("quantity", 1));
            }
        }
        order.itemCount = count;
        return order;
    }
}
