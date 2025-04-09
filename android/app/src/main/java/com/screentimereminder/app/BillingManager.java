package com.screentimereminder.app;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.android.billingclient.api.*;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "BillingManager")
public class BillingManager extends Plugin {
    private static final String TAG = "BillingManager";
    private BillingClient billingClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    // Product IDs for tabs
    private static final String HEATMAP_TAB_ID = "heatmap_tab";
    private static final String TIMELINE_TAB_ID = "timeline_tab";
    private static final String INSIGHTS_TAB_ID = "insights_tab";
    private static final String DETAILS_TAB_ID = "details_tab";
    private static final String FOCUS_TAB_ID = "focus_tab";
    private static final String ALL_TABS_ID = "all_tabs_bundle";

    @Override
    public void load() {
        super.load();
        setupBillingClient();
    }

    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(getContext())
            .setListener(this::handlePurchases)
            .enablePendingPurchases()
            .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected");
                    queryPurchases();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.d(TAG, "Billing client disconnected");
                // Retry connection
                setupBillingClient();
            }
        });
    }

    @PluginMethod
    public void getProducts(PluginCall call) {
        executorService.execute(() -> {
            try {
                List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
                productList.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(HEATMAP_TAB_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build());
                // Add other products...

                QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(productList)
                    .build();

                billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        JSObject ret = new JSObject();
                        JSONArray products = new JSONArray();
                        
                        for (ProductDetails details : productDetailsList) {
                            try {
                                JSONObject product = new JSONObject();
                                product.put("id", details.getProductId());
                                product.put("title", details.getTitle());
                                product.put("description", details.getDescription());
                                product.put("price", details.getOneTimePurchaseOfferDetails().getFormattedPrice());
                                products.put(product);
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing product details", e);
                            }
                        }
                        
                        ret.put("products", products.toString());
                        call.resolve(ret);
                    } else {
                        call.reject("Failed to get products");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error getting products", e);
                call.reject("Error getting products: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void purchaseProduct(PluginCall call) {
        if (!call.getData().has("productId")) {
            call.reject("Product ID is required");
            return;
        }

        String productId = call.getString("productId");
        Activity activity = getActivity();
        
        if (activity == null) {
            call.reject("Activity not available");
            return;
        }

        executorService.execute(() -> {
            try {
                QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                    .setProductList(List.of(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    ))
                    .build();

                billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && !productDetailsList.isEmpty()) {
                        ProductDetails productDetails = productDetailsList.get(0);
                        
                        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(List.of(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .build()
                            ))
                            .build();

                        billingClient.launchBillingFlow(activity, flowParams);
                        call.resolve();
                    } else {
                        call.reject("Product not found");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error launching purchase flow", e);
                call.reject("Error launching purchase flow: " + e.getMessage());
            }
        });
    }

    private void handlePurchases(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            // Acknowledge the purchase if it hasn't been acknowledged yet
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();

                billingClient.acknowledgePurchase(params, billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        savePurchase(purchase.getProducts());
                    }
                });
            }
            
            // Save the purchase
            savePurchase(purchase.getProducts());
        }
    }

    private void savePurchase(List<String> productIds) {
        try {
            for (String productId : productIds) {
                getContext()
                    .getSharedPreferences("purchases", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(productId, true)
                    .apply();
            }
            
            // Notify the web app about the purchase
            notifyListeners("purchaseUpdated", new JSObject());
        } catch (Exception e) {
            Log.e(TAG, "Error saving purchase", e);
        }
    }

    @PluginMethod
    public void checkPurchases(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            JSONObject purchases = new JSONObject();
            
            // Check each product
            String[] products = {
                HEATMAP_TAB_ID,
                TIMELINE_TAB_ID,
                INSIGHTS_TAB_ID,
                DETAILS_TAB_ID,
                FOCUS_TAB_ID,
                ALL_TABS_ID
            };
            
            for (String productId : products) {
                boolean isPurchased = getContext()
                    .getSharedPreferences("purchases", Context.MODE_PRIVATE)
                    .getBoolean(productId, false);
                purchases.put(productId, isPurchased);
            }
            
            ret.put("purchases", purchases.toString());
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Error checking purchases", e);
            call.reject("Error checking purchases: " + e.getMessage());
        }
    }

    private void queryPurchases() {
        executorService.execute(() -> {
            try {
                billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                    this::handlePurchases
                );
            } catch (Exception e) {
                Log.e(TAG, "Error querying purchases", e);
            }
        });
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        if (billingClient != null) {
            billingClient.endConnection();
        }
        executorService.shutdown();
    }
} 