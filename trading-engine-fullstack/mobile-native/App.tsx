import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, View, SafeAreaView, TouchableOpacity, ScrollView, TextInput, Alert } from 'react-native';
import { StatusBar } from 'expo-status-bar';

const API_BASE = 'http://10.0.2.2:8080'; // Android emulator localhost or your machine LAN IP

export default function App() {
  const [activeTab, setActiveTab] = useState<'watch' | 'trade' | 'orders' | 'portfolio'>('trade');
  const [currentTrader, setCurrentTrader] = useState('T001');
  const [ticker, setTicker] = useState('AAPL');
  const [bids, setBids] = useState<any[]>([]);
  const [asks, setAsks] = useState<any[]>([]);
  const [price, setPrice] = useState(100.0);
  const [orderQty, setOrderQty] = useState('10');
  const [orderPrice, setOrderPrice] = useState('100.00');
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY');

  useEffect(() => {
    fetchBook();
    const interval = setInterval(fetchBook, 2000);
    return () => clearInterval(interval);
  }, [ticker]);

  const fetchBook = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/stocks/${ticker}/book`);
      if (res.ok) {
        const data = await res.json();
        setBids(data.bids || []);
        setAsks(data.asks || []);
        setPrice(data.lastPrice || 100.0);
      }
    } catch (e) {
      // offline / local simulation
    }
  };

  const submitOrder = async () => {
    try {
      const res = await fetch(`${API_BASE}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          traderId: currentTrader,
          ticker: ticker,
          side: side,
          price: parseFloat(orderPrice),
          quantity: parseInt(orderQty),
          type: 'LIMIT'
        })
      });
      const data = await res.json();
      if (data.success) {
        Alert.alert('Success', `Order ${data.orderId} submitted!`);
        fetchBook();
      } else {
        Alert.alert('Rejected', data.message);
      }
    } catch (err) {
      Alert.alert('Error', 'Could not connect to matching engine API.');
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="light" />
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.brandTitle}>APEX TRADER</Text>
        <View style={styles.traderBadge}>
          <Text style={styles.traderText}>Trader: {currentTrader}</Text>
        </View>
      </View>

      {/* Main Content */}
      <ScrollView style={styles.content}>
        {/* Ticker & Price Header */}
        <View style={styles.priceCard}>
          <Text style={styles.tickerSymbol}>{ticker}</Text>
          <Text style={styles.lastPrice}>${price.toFixed(2)}</Text>
        </View>

        {/* Level 2 Order Book Depth */}
        <Text style={styles.sectionTitle}>Level-2 Order Book</Text>
        <View style={styles.bookContainer}>
          <View style={styles.halfCol}>
            <Text style={styles.bookColHeadGreen}>BID (BUY)</Text>
            {bids.slice(0, 5).map((b, idx) => (
              <View key={idx} style={styles.levelRow}>
                <Text style={styles.qtyText}>{b.quantity}</Text>
                <Text style={styles.bidPrice}>${b.price.toFixed(2)}</Text>
              </View>
            ))}
          </View>
          <View style={styles.halfCol}>
            <Text style={styles.bookColHeadRed}>ASK (SELL)</Text>
            {asks.slice(0, 5).map((a, idx) => (
              <View key={idx} style={styles.levelRow}>
                <Text style={styles.askPrice}>${a.price.toFixed(2)}</Text>
                <Text style={styles.qtyText}>{a.quantity}</Text>
              </View>
            ))}
          </View>
        </View>

        {/* Quick Order Pad */}
        <View style={styles.orderPad}>
          <View style={styles.sideToggle}>
            <TouchableOpacity
              style={[styles.sideBtn, side === 'BUY' && styles.sideBuyActive]}
              onPress={() => setSide('BUY')}
            >
              <Text style={styles.sideText}>BUY</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.sideBtn, side === 'SELL' && styles.sideSellActive]}
              onPress={() => setSide('SELL')}
            >
              <Text style={styles.sideText}>SELL</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.formRow}>
            <Text style={styles.label}>Qty</Text>
            <TextInput
              style={styles.input}
              value={orderQty}
              keyboardType="numeric"
              onChangeText={setOrderQty}
            />
          </View>

          <View style={styles.formRow}>
            <Text style={styles.label}>Price</Text>
            <TextInput
              style={styles.input}
              value={orderPrice}
              keyboardType="numeric"
              onChangeText={setOrderPrice}
            />
          </View>

          <TouchableOpacity
            style={[styles.submitBtn, side === 'BUY' ? styles.bgBuy : styles.bgSell]}
            onPress={submitOrder}
          >
            <Text style={styles.submitBtnText}>PLACE {side} ORDER</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>

      {/* Bottom Tabs */}
      <View style={styles.bottomNav}>
        <TouchableOpacity style={styles.navItem} onPress={() => setActiveTab('watch')}>
          <Text style={[styles.navText, activeTab === 'watch' && styles.navActive]}>Watch</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.navItem} onPress={() => setActiveTab('trade')}>
          <Text style={[styles.navText, activeTab === 'trade' && styles.navActive]}>Trade</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.navItem} onPress={() => setActiveTab('orders')}>
          <Text style={[styles.navText, activeTab === 'orders' && styles.navActive]}>Orders</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.navItem} onPress={() => setActiveTab('portfolio')}>
          <Text style={[styles.navText, activeTab === 'portfolio' && styles.navActive]}>Portfolio</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0b0e14' },
  header: { flexDirection: 'row', justifyContent: 'space-between', padding: 16, borderBottomWidth: 1, borderColor: '#232a38' },
  brandTitle: { color: '#f0f4f8', fontWeight: 'bold', fontSize: 18 },
  traderBadge: { backgroundColor: '#151924', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 6 },
  traderText: { color: '#3861fb', fontWeight: '600', fontSize: 12 },
  content: { flex: 1, padding: 16 },
  priceCard: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  tickerSymbol: { color: '#f0f4f8', fontSize: 24, fontWeight: 'bold' },
  lastPrice: { color: '#00c087', fontSize: 24, fontWeight: 'bold' },
  sectionTitle: { color: '#848e9c', fontSize: 13, fontWeight: '600', marginBottom: 8, textTransform: 'uppercase' },
  bookContainer: { flexDirection: 'row', backgroundColor: '#151924', borderRadius: 8, padding: 10, gap: 10, marginBottom: 16 },
  halfCol: { flex: 1 },
  bookColHeadGreen: { color: '#00c087', fontSize: 11, fontWeight: '700', marginBottom: 6 },
  bookColHeadRed: { color: '#f6465d', fontSize: 11, fontWeight: '700', marginBottom: 6, textAlign: 'right' },
  levelRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 3 },
  bidPrice: { color: '#00c087', fontWeight: '600', fontSize: 13 },
  askPrice: { color: '#f6465d', fontWeight: '600', fontSize: 13 },
  qtyText: { color: '#848e9c', fontSize: 12 },
  orderPad: { backgroundColor: '#151924', borderRadius: 10, padding: 14, gap: 12 },
  sideToggle: { flexDirection: 'row', backgroundColor: '#0b0e14', borderRadius: 6, padding: 2 },
  sideBtn: { flex: 1, paddingVertical: 8, alignItems: 'center', borderRadius: 5 },
  sideBuyActive: { backgroundColor: '#00c087' },
  sideSellActive: { backgroundColor: '#f6465d' },
  sideText: { color: '#fff', fontWeight: 'bold', fontSize: 13 },
  formRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  label: { color: '#848e9c', fontSize: 13 },
  input: { backgroundColor: '#0b0e14', color: '#fff', width: 140, borderRadius: 6, padding: 8, textAlign: 'right' },
  submitBtn: { padding: 14, borderRadius: 8, alignItems: 'center', marginTop: 6 },
  bgBuy: { backgroundColor: '#00c087' },
  bgSell: { backgroundColor: '#f6465d' },
  submitBtnText: { color: '#fff', fontWeight: 'bold', fontSize: 14 },
  bottomNav: { flexDirection: 'row', height: 60, backgroundColor: '#151924', borderTopWidth: 1, borderColor: '#232a38', alignItems: 'center' },
  navItem: { flex: 1, alignItems: 'center' },
  navText: { color: '#848e9c', fontSize: 12, fontWeight: '600' },
  navActive: { color: '#3861fb' }
});
