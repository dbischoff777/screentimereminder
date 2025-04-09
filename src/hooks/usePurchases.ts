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
      loadProducts();
      checkPurchases();
      
      // Listen for purchase updates
      BillingManager.addListener('purchaseUpdated', () => {
        checkPurchases();
      });
    }
  }, []);

  const loadProducts = async () => {
    try {
      const result = await BillingManager.getProducts();
      if (result.products) {
        setProducts(JSON.parse(result.products));
      }
    } catch (error) {
      console.error('Error loading products:', error);
    } finally {
      setLoading(false);
    }
  };

  const checkPurchases = async () => {
    try {
      const result = await BillingManager.checkPurchases();
      if (result.purchases) {
        setPurchases(JSON.parse(result.purchases));
      }
    } catch (error) {
      console.error('Error checking purchases:', error);
    }
  };

  const purchaseProduct = async (productId: string) => {
    try {
      await BillingManager.purchaseProduct({ productId });
      return true;
    } catch (error) {
      console.error('Error purchasing product:', error);
      return false;
    }
  };

  const isTabUnlocked = (tabId: string): boolean => {
    if (!Capacitor.isNativePlatform()) return true; // Always unlocked in web
    return purchases.all_tabs_bundle || purchases[tabId as keyof PurchaseState];
  };

  return {
    products,
    purchases,
    loading,
    purchaseProduct,
    isTabUnlocked
  };
}; 