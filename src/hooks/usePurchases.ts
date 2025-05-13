import { useState, useEffect } from 'react';
import { Capacitor, registerPlugin } from '@capacitor/core';

interface BillingManagerPlugin {
  getProducts(): Promise<{ products: string }>;
  checkPurchases(): Promise<{ purchases: string }>;
  purchaseProduct(options: { productId: string }): Promise<void>;
  addListener(eventName: string, callback: (data?: any) => void): Promise<void>;
}

const BillingManager = registerPlugin<BillingManagerPlugin>('BillingManager');

export interface Product {
  id: string;
  title: string;
  description: string;
  price: string;
}

export interface PurchaseState {
  heatmap_tab: boolean;
  timeline_tab: boolean;
  insights_tab: boolean;
  details_tab: boolean;
  focus_tab: boolean;
  all_tabs_bundle: boolean;
}

// IMPORTANT: Set to false before production release!
const TESTING_MODE = false;

export const usePurchases = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [purchases, setPurchases] = useState<PurchaseState>({
    heatmap_tab: false,
    timeline_tab: false,
    insights_tab: false,
    details_tab: false,
    focus_tab: false,
    all_tabs_bundle: false
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isProcessingPayment, setIsProcessingPayment] = useState(false);

  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      console.log('Running on native platform, initializing billing');
      initializeBilling();
    } else {
      console.log('Running on web, billing disabled');
      setLoading(false);
    }
  }, []);

  const initializeBilling = async () => {
    try {
      await loadProducts();
      await checkPurchases();
      
      // Listen for purchase updates
      BillingManager.addListener('purchaseUpdated', async () => {
        console.log('Purchase updated event received');
        await checkPurchases();
      });

      // Listen for the end of the purchase attempt flow
      BillingManager.addListener('purchaseAttemptFinished', (result: any) => {
        console.log('Purchase attempt finished event received:', result);
        setIsProcessingPayment(false);
        // The `purchaseUpdated` event will handle actual entitlement changes via checkPurchases()
      });
    } catch (err) {
      console.error('Error initializing billing:', err);
      setError(err instanceof Error ? err.message : 'Failed to initialize billing');
    } finally {
      setLoading(false);
    }
  };

  const loadProducts = async () => {
    try {
      console.log('Loading available products');
      const result = await BillingManager.getProducts();
      console.log('Product result:', result);
      
      if (result.products) {
        const parsedProducts = JSON.parse(result.products);
        console.log('Available products:', parsedProducts);
        setProducts(parsedProducts);
      }
    } catch (err) {
      console.error('Error loading products:', err);
      setError(err instanceof Error ? err.message : 'Failed to load products');
      throw err; // Propagate error to caller
    }
  };

  const checkPurchases = async () => {
    try {
      console.log('Checking existing purchases');
      const result = await BillingManager.checkPurchases();
      console.log('Purchases result:', result);
      
      if (result.purchases) {
        const parsedPurchases = JSON.parse(result.purchases);
        console.log('Current purchases state:', parsedPurchases);
        setPurchases(parsedPurchases);
      }
    } catch (err) {
      console.error('Error checking purchases:', err);
      setError(err instanceof Error ? err.message : 'Failed to check purchases');
      throw err; // Propagate error to caller
    }
  };

  const purchaseProduct = async (productId: string): Promise<boolean> => {
    try {
      console.log('Attempting to purchase product:', productId);
      setError(null); // Clear previous errors
      
      if (TESTING_MODE) {
        console.log('TESTING MODE: Simulating successful purchase');
        setIsProcessingPayment(true); // Simulate processing start
        // Simulate successful purchase in testing mode
        if (productId === 'all_tabs_bundle') {
          setPurchases(prev => ({
            ...prev,
            heatmap_tab: true,
            timeline_tab: true,
            insights_tab: true,
            details_tab: true,
            focus_tab: true,
            all_tabs_bundle: true
          }));
        } else {
          setPurchases(prev => ({
            ...prev,
            [productId]: true
          }));
        }
        // Simulate the end of processing for testing mode
        setTimeout(() => {
          setIsProcessingPayment(false);
          // To fully test, you would ideally have a way to trigger the 'purchaseUpdated' 
          // and 'purchaseAttemptFinished' from a mock BillingManager if TESTING_MODE is true.
          // For now, we just simulate the state changes.
          // Conceptual: NativeBridge.triggerEvent('purchaseUpdated', { ... });
          // Conceptual: NativeBridge.triggerEvent('purchaseAttemptFinished', { responseCode: 0 });
          if (typeof (BillingManager as any)._triggerTestEvent === 'function') {
            (BillingManager as any)._triggerTestEvent('purchaseUpdated', { /* mock data */ });
            (BillingManager as any)._triggerTestEvent('purchaseAttemptFinished', { responseCode: 0, productIds: [productId] });
          }
          console.log("TESTING_MODE: Simulating purchaseUpdated & purchaseAttemptFinished events after delay")
        }, 1000);
        return true;
      }
      
      // Normal purchase flow
      setIsProcessingPayment(true); // Indicate that the payment process has started
      await BillingManager.purchaseProduct({ productId });
      // setIsProcessingPayment(false) will be handled by the 'purchaseAttemptFinished' listener
      console.log('Purchase flow launch initiated for:', productId);
      return true; // Indicates launch was attempted
    } catch (err) {
      console.error('Error purchasing product:', err);
      setError(err instanceof Error ? err.message : 'Failed to complete purchase');
      setIsProcessingPayment(false); // Ensure processing is false if launch itself fails
      return false;
    }
  };

  const isTabUnlocked = (tabId: string): boolean => {
    try {
      console.log('PURCHASE-DEBUG: isTabUnlocked called for', tabId);
      
      if (!Capacitor.isNativePlatform()) {
        console.log('PURCHASE-DEBUG: Web platform - all tabs unlocked');
        return true; // Always unlocked in web
      }
      
      // Force log the purchase state for debugging
      console.log('PURCHASE-DEBUG: All tabs bundle status:', purchases.all_tabs_bundle);
      console.log('PURCHASE-DEBUG: Specific tab status:', purchases[tabId as keyof PurchaseState]);
      
      // If TESTING_MODE is enabled, use that for unlocked state
      if (TESTING_MODE) {
        console.log('PURCHASE-DEBUG: In testing mode - simulating unlocked state');
        if (tabId === 'settings_tab') return true; // Settings is always unlocked
        
        // Simulate the real unlock state based on our purchases object
        const unlocked = purchases.all_tabs_bundle || purchases[tabId as keyof PurchaseState];
        console.log('PURCHASE-DEBUG: TESTING_MODE tab status:', unlocked);
        return unlocked;
      }
      
      // Real purchase flow check
      // Ensure `purchases` is an object and `tabId` is a valid key before accessing
      if (typeof purchases !== 'object' || purchases === null) {
        console.error('PURCHASE-DEBUG: purchases object is invalid', purchases);
        return false; // Default to locked if purchases object is malformed
      }
      
      const unlocked = purchases.all_tabs_bundle || purchases[tabId as keyof PurchaseState];
      console.log('PURCHASE-DEBUG: Tab', tabId, 'unlock status:', !!unlocked);
      return !!unlocked; // Ensure boolean conversion
    } catch (err) {
      console.error('PURCHASE-DEBUG: Error in isTabUnlocked:', err);
      setError(err instanceof Error ? err.message : 'Failed to check tab unlock status');
      // Default to locked (false) in case of errors to ensure purchase prompt if status is uncertain
      return false;
    }
  };

  return {
    products,
    purchases,
    loading,
    error,
    isProcessingPayment,
    purchaseProduct,
    isTabUnlocked,
    refreshPurchases: checkPurchases
  };
}; 