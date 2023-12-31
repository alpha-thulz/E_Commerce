package za.co.tyaphile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.plugin.bundled.CorsPluginConfig;
import kong.unirest.JsonNode;
import za.co.tyaphile.database.DatabaseManager;
import za.co.tyaphile.order.Order;
import za.co.tyaphile.order.OrdersDB;
import za.co.tyaphile.product.Product;
import za.co.tyaphile.product.ProductsDB;
import za.co.tyaphile.user.User;
import za.co.tyaphile.user.UserDB;

import java.sql.SQLException;
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
            cfg.plugins.enableCors(cors -> cors.add(CorsPluginConfig::anyHost));
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
//        new DatabaseManager(":memory:");
        new DatabaseManager("commerce.db");
//        createMockProducts();  // Comment out to disable creating mock items
        init();

        server.post("/product", this::addProduct);
        server.post("/remove-products", this::deleteProduct);
        server.post("/products", this::getProductList);

        server.get("/product/{id}", this::getProduct);
        server.put("/product/{id}", this::updateProduct);

        server.post("/order", this::addOrder);
        server.put("/order/{id}", this::updateOrder);
        server.post("/orders", this::getOrder);
        server.get("/order/{id}", this::getOrder);

        server.post("/customer", this::addUser);
        server.delete("/customer/{id}", this::deleteUser);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop()));
    }

    private void updateOrder(Context context) {
        Map<?, ?> request = (Map<?, ?>) new Gson().fromJson(context.body(), Map.class);

        if (!request.containsKey("customerId") ||
                (request.get("customerId").toString() == null || request.get("customerId").toString().isBlank())) {
            context.status(HttpStatus.BAD_REQUEST);
            context.json(getErrorMessage(HttpStatus.BAD_REQUEST, "You need to be signed in to pay for the order"));
        } else {
            if (request.containsKey("paid") && ((Boolean) request.get("paid"))) {
                String customerId = request.get("customerId").toString();
                String orderId = request.get("id").toString();
                DatabaseManager.makePayment(customerId);
                context.json(getOrderJson(DatabaseManager.getCustomerOrder(customerId, orderId)));
            }
        }
    }

    private void getOrder(Context context) {
        Map<?, ?> request = (Map<?, ?>) new Gson().fromJson(context.body(), Map.class);

        if (request == null) {
            context.json(getOrderJson(DatabaseManager.getOrder(context.pathParamMap().get("id"))));
        } else {
            if (!request.containsKey("customerId") ||
                    (request.get("customerId").toString() == null || request.get("customerId").toString().isBlank())) {
                context.status(HttpStatus.BAD_REQUEST);
                context.json(getErrorMessage(HttpStatus.BAD_REQUEST, "You need to be signed in to place an order"));
            } else {
                context.json(getOrderJson(DatabaseManager.getCustomerOrder(request.get("customerId").toString())));
            }
        }
    }

    private void addOrder(Context context) {
        Map<?, ?> request = (Map<?, ?>) new Gson().fromJson(context.body(), Map.class);
        if (!request.containsKey("customerId") ||
                (request.get("customerId").toString() == null || request.get("customerId").toString().isBlank())) {
            context.status(HttpStatus.BAD_REQUEST);
            context.json(getErrorMessage(HttpStatus.BAD_REQUEST, "You need to be signed in to place an order"));
        } else {
            String customerID = request.get("customerId").toString();
            String productsString = request.get("products").toString();

            String[] products;
            if (productsString.startsWith("[") && productsString.endsWith("]")) {
                List<String> productList = Arrays.stream(productsString.substring(1, productsString.length() - 1)
                        .replaceAll("\"", "").split(",")).toList();
                products = productList.toArray(new String[0]);
            } else {
                products = new String[] {productsString};
            }

            boolean isOrdered = DatabaseManager.addOrder(customerID, products);
            if (isOrdered) {
                context.json(getOrderJson(DatabaseManager.getCustomerOrder(customerID)));
            } else {
                context.json(getErrorMessage(HttpStatus.BAD_REQUEST, "Failed to placed an order"));
            }
        }
    }

    private void deleteUser(Context context) {
        String id = context.pathParam("id");
        DatabaseManager.removeUser(id);
    }

    private void addUser(Context context) {
        Map<?, ?> request = (Map<?, ?>) new Gson().fromJson(context.body(), Map.class);

        User user = DatabaseManager.getUser(request.get("name").toString(), request.get("email").toString());
        if (user == null) {
            boolean isAdded = DatabaseManager.addUser(request.get("name").toString(), request.get("email").toString());

            if (isAdded) {
                user = DatabaseManager.getUser(request.get("name").toString(), request.get("email").toString());

                assert user != null;
                context.json(getUserJson(user));
            } else {
                context.status(HttpStatus.BAD_REQUEST);
                context.json(getErrorMessage(HttpStatus.BAD_REQUEST, "Failed to add user, email address already in use"));
            }
        } else {
            context.json(getUserJson(user));
        }
    }

    private void getProductList(Context context) {
        List<Product> allProducts;
        List<String> response;

//        Map<String, String> response = allProducts.stream()
//                .collect(Collectors.toMap(Product::getProductId, this::getProductJson));

        try {
            ArrayList<?> items = (ArrayList<?>) new Gson().fromJson(context.body(), ArrayList.class);
            allProducts = items.stream().map(x -> DatabaseManager.getProduct(String.valueOf(x))).toList();
            response = allProducts.stream().map(this::getProductJson).collect(Collectors.toList());
        } catch (Exception e) {
            allProducts = DatabaseManager.getAllProducts();
            response = allProducts.stream().map(this::getProductJson).collect(Collectors.toList());
        }

        context.json(new Gson().toJson(response));
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
            items.forEach(x -> DatabaseManager.removeProduct(x.toString()));
            context.status(HttpStatus.OK);
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

    private void updateProduct(Context ctx) {
        final JsonNode node = new JsonNode(ctx.body());
        try {
             boolean isUpdated = DatabaseManager.updateProduct(node.getObject().getString("id"),
                     node.getObject().getString("name"),
                     node.getObject().getString("description"),
                     Double.parseDouble(node.getObject().getString("price")));
             if (isUpdated) {
                 ctx.status(HttpStatus.OK);
             } else {
                 ctx.status(HttpStatus.NOT_MODIFIED);
             }
             getProduct(ctx);
        } catch (SQLException e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(getErrorMessage(HttpStatus.BAD_REQUEST, e.getMessage()));
            throw new RuntimeException(e);
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

    private void createMockProducts() {
        Map<String, List<Object>> products = new HashMap<>();
        products.put("Designer Concepts Yves Plasma Stand - White 2.2m",
                new ArrayList<>(List.of("The Yves Plasma Stand Brings a Light " +
                "Concept To Your Living Room Through Its Straight Lines And Soft Colors", 6999)));
        products.put("OPPO Find N2 Flip 5G 256GB Dual Sim - Moonlit Purple",
                new ArrayList<>(List.of("OPPO Find N2 Flip 5G\n" +
                        "Discover the tech-savvy, style statement phone that lets you see more in a snap. " +
                        "The larger cover screen expands your view with innovative features, whilst the foldable design" +
                        " reimagines photography possibilities, all in a gorgeous and flawless design that " +
                        "you can pop in your pocket.", 23999)));
        products.put("Volkano SA Travel Plug to UK Plug Traveller Series",
                new ArrayList<>(List.of("There's nothing worse than arriving in a country with different sockets to " +
                        "the Type-M plug standard. Prepare in advance with this handy plug converter. Note, this " +
                        "adapter does not convert voltage, so make sure you use it with devices that have a " +
                        "universal input voltage range.", 149)));
        products.put("NIVEA Radiant & Beauty",
                new ArrayList<>(List.of("NIVEA Radiant & Beauty Even Glow Body Cream with 95% Pure Vitamin C, 400ml", 159)));
        products.put("GARDENA Garden Shower Solo", new ArrayList<>(List.of("Extends to maximum 207 cm in height\n" +
                "Height adjustable lever\n" +
                "Gentle soft spray pattern\n" +
                "Integrated ground spike for easy installation", 369)));
        products.put("GARDENA Rain Water Tank Pump 4700/2 inox", new ArrayList<>(List.of("40 yrs of innovation\n" +
                "Reliable engineering\n" +
                "Global market leader\n" +
                "Safe and reliable\n" +
                "High quality materials\n" +
                "Convenient\n" +
                "Versatile\n" +
                "Eco Friendly", 2199)));
        products.put("Everfurn Work Desk - Anthony Series",
                new ArrayList<>(List.of("A stunningly comprised work desk that boasts ample space with " +
                        "additional storage space. Sturdy and durable, the Anthony Series is also a looker with " +
                        "clean lines and design precision.", 1699)));
        products.put("Brother DCP-T720DW Ink Tank Printer 3in1 with WiFi and ADF",
                new ArrayList<>(List.of("Specifications:\n" +
                        "- Functions: Print, Copy, Scan with Auto two-sided print\n" +
                        "- Connectivity: USB, Wireless\n" +
                        "- Print speed: Up to 17/16.5 ipm (ISO)\n" +
                        "- Paper Input: 150 Sheets and 20 sheet Auto Document Feeder,\n" +
                        "- Single Sheet manual feed slot (max paper gsm - 300gsm)", 5499)));
        products.put("Brother BT5000 / 6000 Combo Ink Bottle Set", new ArrayList<>(List.of("Compatible Printers\n" +
                "\n" +
                "Brother DCP-T300\n" +
                "Brother DCP-T500W\n" +
                "Brother DCP-T700W\n" +
                "Brother MFC-T800W\n" +
                "Black - 115ml/Colours - 45ml\n" +
                "Page Yield - 4000/5 000 pgs @ 5% coverage\n" +
                "High quality printing and page yields equal to Original", 154)));
        products.put("Sugar", new ArrayList<>(List.of("1 kg of sweetener", 24.99)));

        products.forEach((key, info) -> DatabaseManager.addProduct(key, info.get(0).toString(), Double.parseDouble(info.get(1).toString())));
    }
}
