package za.co.tyaphile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import kong.unirest.JsonNode;
import za.co.tyaphile.database.DatabaseManager;
import za.co.tyaphile.order.Order;
import za.co.tyaphile.order.OrdersDB;
import za.co.tyaphile.product.Product;
import za.co.tyaphile.product.ProductsDB;
import za.co.tyaphile.user.User;
import za.co.tyaphile.user.UserDB;

import java.util.*;
import java.util.stream.Collectors;

public class ECommerceServer {
    private Javalin server;
    private final int DEFAULT_PORT = 5000;
    private final ProductsDB products = new ProductsDB();
    private final UserDB users = new UserDB();
    private final OrdersDB ordersDB = new OrdersDB();

    private void init() {
        server = Javalin.create(cfg -> {
            cfg.http.defaultContentType = "application/json";
            cfg.showJavalinBanner = false;
        });
    }

    public void start() {
        start(DEFAULT_PORT);
    }

    public void start(int PORT) {
        server.start(PORT);
    }

    public void stop() {
        server.stop();
    }

    public ECommerceServer() {
        init();

        new DatabaseManager();

        server.post("/product", this::addProduct);
        server.post("/remove-products", this::deleteProduct);
        server.post("/products", this::getProductList);
        server.get("/product/{id}", this::getProduct);

        server.post("/order", this::addOrder);

        server.post("/customer", this::addUser);
        server.delete("/customer/{id}", this::deleteUser);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop()));
    }

    private void addOrder(Context context) {
        Map<?, ?> request = (Map<?, ?>) new Gson().fromJson(context.body(), Map.class);

        Optional<Product> prod = products.getAllProducts().stream()
                .filter(x -> x.getProductId().equals(request.get("products").toString()))
                .findFirst();
        if (prod.isPresent()) {
            Product product = prod.get();
            Order order = new Order(request.get("customerId").toString());
            order.getOrderedProducts().add(request.get("products").toString());
            order.setTotal((float) product.getPrice());

            ordersDB.addOrder(order);
//            System.out.println(" > " + new Gson().toJson(order));
        }
    }

    private void deleteUser(Context context) {
        String id = context.pathParam("id");
        DatabaseManager.removeUser(id);
    }

    private void addUser(Context context) {
        Map<?, ?> request = (Map<?, ?>) new Gson().fromJson(context.body(), Map.class);

        DatabaseManager.addUser(request.get("name").toString(), request.get("email").toString());
        User user = DatabaseManager.getUser(request.get("name").toString(), request.get("email").toString());

        assert user != null;
        context.json(getUserJson(user));
    }

    private void getProductList(Context context) {
        List<Product> allProducts;
        try {
            ArrayList<?> items = (ArrayList<?>) new Gson().fromJson(context.body(), ArrayList.class);
            allProducts = items.stream().map(x -> DatabaseManager.getProduct(String.valueOf(x))).toList();
            List<String> response = allProducts.stream().map(this::getProductJson).collect(Collectors.toList());
            context.json(response);
        } catch (Exception e) {
            allProducts = DatabaseManager.getAllProducts();
            List<String> response = allProducts.stream().map(this::getProductJson).collect(Collectors.toList());
            context.json(response);
        }
    }

    private void getProduct(Context context) {
        Product product = DatabaseManager.getProduct(context.pathParam("id"));
        if (product != null) {
            context.status(HttpStatus.OK);
            context.json(getProductJson(product));
        } else {
            context.status(HttpStatus.NOT_FOUND);
            context.json(getErrorMessage(HttpStatus.NOT_FOUND, "Product ID: " + context.pathParam("id") + " not found"));
        }
    }

    private void deleteProduct(Context context) {
        try {
            ArrayList<?> items = (ArrayList<?>) new Gson().fromJson(context.body(), ArrayList.class);
            context.status(HttpStatus.OK);
            items.forEach(x -> DatabaseManager.removeProduct(x.toString()));
        } catch (Exception e) {
            context.status(HttpStatus.BAD_REQUEST);
            context.json(getErrorMessage(HttpStatus.BAD_REQUEST, e.getMessage()));
        }
    }

    private void addProduct(Context ctx) {
        final JsonNode node = new JsonNode(ctx.body());
        try {
            Product product = DatabaseManager.addProduct(node.getObject().getString("name"),
                    node.getObject().getString("description"),
                    Double.parseDouble(node.getObject().getString("price")));
            ctx.status(HttpStatus.OK);

            assert product != null;
            ctx.json(getProductJson(product));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(getErrorMessage(HttpStatus.BAD_REQUEST, e.getMessage()));
        }
    }

    private Map<String, Object> getErrorMessage(HttpStatus status, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", status);
        result.put("title", "Error");
        result.put("message", message);
        return result;
    }

    private String getOrderJson(Order order) {
        Gson json = new Gson();

        Map<String, Object> orderInfo = new HashMap<>();
        orderInfo.put("id", order.getOrderId());
        orderInfo.put("paid", order.isPaid());
        orderInfo.put("customerId", order.getCustomerId());
        orderInfo.put("products", order.getOrderedProducts());
        orderInfo.put("total", order.getTotal());

        return json.toJson(orderInfo);
    }

    private String getUserJson(User user) {
        JsonObject json = new JsonObject();
        json.addProperty("id", user.getId());
        json.addProperty("name", user.getName());
        json.addProperty("email", user.getEmail());
        return json.toString();
    }

    private String getProductJson(Product product) {
        JsonObject json = new JsonObject();
        json.addProperty("id", product.getProductId());
        json.addProperty("name", product.getProductName());
        json.addProperty("description", product.getProductDescription());
        json.addProperty("price", product.getPrice());

        return json.toString();
    }

    public static void main(String[] args) {
        ECommerceServer server = new ECommerceServer();
        server.start();
    }
}
