import { useState, useEffect } from 'react';
import { Capacitor, registerPlugin } from '@capacitor/core';

interface BillingManagerPlugin {
  getProducts(): Promise<{ products: string }>;
  checkPurchases(): Promise<{ purchases: string }>;
  purchaseProduct(options: { productId: string }): Promise<void>;
  addListener(eventName: string, callback: () => void): Promise<void>;
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

  useEffect(() => {
    if (Capacitor.isNativePlatform()) {
      console.log('Running on native platform, initializing billing');
      loadProducts();
      checkPurchases();
      
      // Listen for purchase updates
      BillingManager.addListener('purchaseUpdated', () => {
        console.log('Purchase updated event received');
        checkPurchases();
      });
    } else {
      console.log('Running on web, billing disabled');
      setLoading(false);
    }
  }, []);

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
    } catch (error) {
      console.error('Error loading products:', error);
    } finally {
      setLoading(false);
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
    } catch (error) {
      console.error('Error checking purchases:', error);
    }
  };

  const purchaseProduct = async (productId: string) => {
    try {
      console.log('Attempting to purchase product:', productId);
      
      if (TESTING_MODE) {
        console.log('TESTING MODE: Simulating successful purchase');
        // Simulate successful purchase in testing mode
        // This is a temporary override for UI testing only
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
        return true;
      }
      
      // Normal purchase flow
      await BillingManager.purchaseProduct({ productId });
      console.log('Purchase flow completed for:', productId);
      // Force check for purchases after purchase attempt
      await checkPurchases();
      return true;
    } catch (error) {
      console.error('Error purchasing product:', error);
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
      const unlocked = purchases.all_tabs_bundle || purchases[tabId as keyof PurchaseState];
      console.log('PURCHASE-DEBUG: Tab', tabId, 'unlock status:', unlocked);
      return unlocked;
    } catch (error) {
      console.error('PURCHASE-DEBUG: Error in isTabUnlocked:', error);
      // Return true in case of errors to avoid blocking user
      return true;
    }
  };

  return {
    products,
    purchases,
    loading,
    purchaseProduct,
    isTabUnlocked
  };
}; 