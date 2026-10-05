# Apex Trader - React Native Mobile Application

This directory contains the cross-platform React Native (Expo) mobile frontend for the Stock Order Matching Engine.

## Requirements
- Node.js (v18+)
- Expo Go app on your iOS or Android phone (or Android Studio emulator)

## Setup & Run

1. **Install Dependencies:**
   ```bash
   npm install
   ```

2. **Configure Backend URL:**
   In `App.tsx`, set `API_BASE`:
   - Android Emulator: `http://10.0.2.2:8080`
   - Real Device on Wi-Fi: `http://<YOUR_COMPUTER_IP>:8080` (e.g. `http://192.168.1.50:8080`)

3. **Start Expo Dev Server:**
   ```bash
   npx expo start
   ```

4. **Scan QR Code:**
   Open the Expo Go app on your phone and scan the terminal QR code to load the app natively!
