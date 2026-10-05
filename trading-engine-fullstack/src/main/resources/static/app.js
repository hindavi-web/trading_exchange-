// =============================================================================
// Apex Exchange Mobile App JavaScript
// =============================================================================

const API_BASE = window.location.origin;

// State
let currentUser = null;
let currentTraderId = 'T001';
let currentTicker = 'AAPL';
let orderSide = 'BUY';
let orderType = 'LIMIT';
let currentOrderSubTab = 'open';
let priceHistory = [98.5, 99.0, 99.2, 100.0, 100.5, 100.0];

// DOM Ready
document.addEventListener('DOMContentLoaded', () => {
    initEventListeners();
    initSSE();
    checkAuthSession();
    initMiniChart();
});

// --- Auth & Session Management ---
function checkAuthSession() {
    const saved = localStorage.getItem('apex_user');
    if (saved) {
        try {
            currentUser = JSON.parse(saved);
            applyAuthenticatedUser(currentUser);
            return;
        } catch (e) {
            localStorage.removeItem('apex_user');
        }
    }
    // If not authenticated, show screen-auth
    showAuthScreen();
}

function showAuthScreen() {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.getElementById('screen-auth').classList.add('active');
    const bNav = document.querySelector('.bottom-nav');
    if (bNav) bNav.style.display = 'none';
    const hdr = document.querySelector('.app-header');
    if (hdr) hdr.style.display = 'none';
}

function switchAuthTab(tab) {
    const tabLogin = document.getElementById('btnTabLogin');
    const tabSignup = document.getElementById('btnTabSignup');
    const formLogin = document.getElementById('formLogin');
    const formSignup = document.getElementById('formSignup');
    const heroH2 = document.querySelector('#screen-auth .auth-hero h2');
    const heroSub = document.querySelector('#screen-auth .auth-hero .auth-sub');

    if (tab === 'login') {
        tabLogin.classList.add('active');
        tabSignup.classList.remove('active');
        formLogin.classList.remove('hidden');
        formSignup.classList.add('hidden');
        if (heroH2) heroH2.textContent = 'Welcome Back';
        if (heroSub) heroSub.textContent = 'Sign in to your trading account';
    } else {
        tabSignup.classList.add('active');
        tabLogin.classList.remove('active');
        formSignup.classList.remove('hidden');
        formLogin.classList.add('hidden');
        if (heroH2) heroH2.textContent = 'Create Account';
        if (heroSub) heroSub.textContent = 'Start trading with $100,000 virtual cash';
    }
}

async function handleLoginSubmit(e) {
    e.preventDefault();
    const username = document.getElementById('loginUsername').value.trim();
    const password = document.getElementById('loginPassword').value;
    const errBox = document.getElementById('loginError');
    const btn = document.getElementById('btnLoginSubmit');
    errBox.classList.add('hidden');
    btn.disabled = true;
    btn.textContent = 'Signing in…';

    try {
        const res = await fetch(`${API_BASE}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const data = await res.json();
        if (data.success) {
            currentUser = data.user;
            localStorage.setItem('apex_user', JSON.stringify(currentUser));
            applyAuthenticatedUser(currentUser);
        } else {
            errBox.innerText = data.message || 'Login failed. Check your credentials.';
            errBox.classList.remove('hidden');
        }
    } catch (err) {
        errBox.innerText = 'Cannot reach server. Is it running?';
        errBox.classList.remove('hidden');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Sign In';
    }
}

async function handleSignupSubmit(e) {
    e.preventDefault();
    const fullName = document.getElementById('signupFullName').value.trim();
    const username = document.getElementById('signupUsername').value.trim();
    const email = document.getElementById('signupEmail').value.trim();
    const password = document.getElementById('signupPassword').value;
    const errBox = document.getElementById('signupError');
    const btn = document.getElementById('btnSignupSubmit');
    errBox.classList.add('hidden');
    btn.disabled = true;
    btn.textContent = 'Creating account…';

    try {
        const res = await fetch(`${API_BASE}/api/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName, username, email, password })
        });
        const data = await res.json();
        if (data.success) {
            currentUser = data.user;
            localStorage.setItem('apex_user', JSON.stringify(currentUser));
            applyAuthenticatedUser(currentUser);
        } else {
            errBox.innerText = data.message || 'Registration failed.';
            errBox.classList.remove('hidden');
        }
    } catch (err) {
        errBox.innerText = 'Cannot reach server. Is it running?';
        errBox.classList.remove('hidden');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Create Account — Get $100,000';
    }
}


function applyAuthenticatedUser(user) {
    currentTraderId = user.id;

    // Show header and nav
    const hdr = document.querySelector('.app-header');
    if (hdr) hdr.style.display = 'flex';
    const bNav = document.querySelector('.bottom-nav');
    if (bNav) bNav.style.display = 'flex';

    // Update header trader label
    const hdrId = document.getElementById('hdrTraderId');
    if (hdrId) hdrId.innerText = user.username || user.id;

    // Update Profile card in Account tab
    const avatar = document.getElementById('userAvatar');
    if (avatar) avatar.innerText = (user.fullName || user.username || 'U').charAt(0).toUpperCase();
    const nameElem = document.getElementById('profileFullName');
    if (nameElem) nameElem.innerText = user.fullName || user.username;
    const userElem = document.getElementById('profileUsername');
    if (userElem) userElem.innerText = `@${user.username} • ID: ${user.id}`;

    switchScreen('watchlist');
    refreshAll();
}




function logout() {
    localStorage.removeItem('apex_user');
    currentUser = null;
    showAuthScreen();
}

function initEventListeners() {
    // No global dropdown; user is always themselves after login
}

function initSSE() {
    const liveDot = document.getElementById('liveDot');
    const liveText = document.getElementById('liveText');

    const evtSource = new EventSource(`${API_BASE}/api/stream`);

    evtSource.onopen = () => {
        liveDot.style.backgroundColor = 'var(--green-up)';
        liveText.innerText = 'LIVE';
    };

    evtSource.onerror = () => {
        liveDot.style.backgroundColor = 'var(--red-down)';
        liveText.innerText = 'OFFLINE';
    };

    evtSource.addEventListener('trade', (e) => {
        try {
            const trade = JSON.parse(e.data);
            handleNewTrade(trade);
        } catch (err) {}
    });

    evtSource.addEventListener('book_update', (e) => {
        try {
            const book = JSON.parse(e.data);
            if (book.ticker === currentTicker) {
                renderOrderBook(book);
            }
        } catch (err) {}
    });

    evtSource.addEventListener('stock_update', (e) => {
        try {
            const stock = JSON.parse(e.data);
            if (stock.ticker === currentTicker) {
                renderStockStats(stock);
            }
            refreshWatchlist();
        } catch (err) {}
    });

    evtSource.addEventListener('order_update', (e) => {
        refreshOrders();
        refreshAccount();
        refreshPortfolio();
    });

    evtSource.addEventListener('circuit_breaker', (e) => {
        try {
            const cb = JSON.parse(e.data);
            toggleCircuitBanner(cb.paused, cb.ticker);
        } catch (err) {}
    });
}

// Global Refresh
function refreshAll() {
    refreshWatchlist();
    refreshOrderBook();
    refreshTrades();
    refreshOrders();
    refreshPortfolio();
    refreshAccount();
}

// 1. Markets & Watchlist
async function refreshWatchlist() {
    try {
        const res = await fetch(`${API_BASE}/api/stocks`);
        const stocks = await res.json();
        const container = document.getElementById('stockListContainer');
        container.innerHTML = '';

        stocks.forEach(s => {
            const isPaused = s.tradingPaused;
            const card = document.createElement('div');
            card.className = 'stock-card';
            card.onclick = () => selectStock(s.ticker);

            card.innerHTML = `
                <div class="stock-main">
                    <div class="stock-ticker">
                        <span>${s.ticker}</span>
                        ${isPaused ? '<span class="badge-halted">HALTED</span>' : ''}
                    </div>
                    <span class="stock-name">Tick: $${s.tickSize.toFixed(2)}</span>
                </div>
                <div class="stock-pricing text-mono">
                    <span class="stock-price ${s.lastTradedPrice >= s.openPrice ? 'text-green' : 'text-red'}">
                        $${s.lastTradedPrice.toFixed(2)}
                    </span>
                    <span class="stock-vol">Vol: ${s.volume.toLocaleString()}</span>
                </div>
            `;
            container.appendChild(card);

            if (s.ticker === currentTicker) {
                renderStockStats(s);
            }
        });
    } catch (e) {
        console.error('Error fetching stocks:', e);
    }
}

function selectStock(ticker) {
    currentTicker = ticker;
    document.getElementById('tradeTicker').innerText = ticker;
    document.getElementById('pausedTicker').innerText = ticker;
    refreshOrderBook();
    refreshTrades();
    switchScreen('trade');
}

// 2. Market Depth & Order Book
async function refreshOrderBook() {
    try {
        const res = await fetch(`${API_BASE}/api/stocks/${currentTicker}/book`);
        if (!res.ok) return;
        const book = await res.json();
        renderOrderBook(book);
    } catch (e) {
        console.error('Error fetching book:', e);
    }
}

function renderOrderBook(book) {
    toggleCircuitBanner(book.tradingPaused, book.ticker);

    const bidsList = document.getElementById('bidsList');
    const asksList = document.getElementById('asksList');
    bidsList.innerHTML = '';
    asksList.innerHTML = '';

    const maxBidQty = book.bids.reduce((m, b) => Math.max(m, b.quantity), 1);
    const maxAskQty = book.asks.reduce((m, a) => Math.max(m, a.quantity), 1);
    const maxQty = Math.max(maxBidQty, maxAskQty, 1);

    // Calculate spread
    if (book.bids.length > 0 && book.asks.length > 0) {
        const spread = Math.max(0, book.asks[0].price - book.bids[0].price);
        document.getElementById('spreadDisplay').innerText = `Spread: $${spread.toFixed(2)}`;
    } else {
        document.getElementById('spreadDisplay').innerText = `Spread: --`;
    }

    // Render Bids
    book.bids.slice(0, 8).forEach(b => {
        const pct = Math.min(100, (b.quantity / maxQty) * 100);
        const row = document.createElement('div');
        row.className = 'level-row text-mono';
        row.innerHTML = `
            <div class="bg-bar" style="width: ${pct}%"></div>
            <span class="orders-tag">${b.count}</span>
            <span>${b.quantity}</span>
            <span class="price-tag">$${b.price.toFixed(2)}</span>
        `;
        bidsList.appendChild(row);
    });

    // Render Asks
    book.asks.slice(0, 8).forEach(a => {
        const pct = Math.min(100, (a.quantity / maxQty) * 100);
        const row = document.createElement('div');
        row.className = 'level-row text-mono';
        row.innerHTML = `
            <div class="bg-bar" style="width: ${pct}%"></div>
            <span class="price-tag">$${a.price.toFixed(2)}</span>
            <span>${a.quantity}</span>
            <span class="orders-tag">${a.count}</span>
        `;
        asksList.appendChild(row);
    });
}

function renderStockStats(s) {
    document.getElementById('tradePrice').innerText = `$${s.lastTradedPrice.toFixed(2)}`;
    document.getElementById('statOpen').innerText = `$${s.openPrice.toFixed(2)}`;
    document.getElementById('statHigh').innerText = `$${s.highPrice.toFixed(2)}`;
    document.getElementById('statLow').innerText = `$${(s.lowPrice > 0 && s.lowPrice < 1e9) ? s.lowPrice.toFixed(2) : s.lastTradedPrice.toFixed(2)}`;
    document.getElementById('statVol').innerText = s.volume.toLocaleString();
    document.getElementById('tradeTickSize').innerText = `Tick: $${s.tickSize.toFixed(2)}`;
    document.getElementById('tickHint').innerText = `Tick size: $${s.tickSize.toFixed(2)}`;
}

// 3. Trade Tape
async function refreshTrades() {
    try {
        const res = await fetch(`${API_BASE}/api/stocks/${currentTicker}/trades`);
        if (!res.ok) return;
        const trades = await res.json();
        const list = document.getElementById('tapeList');
        list.innerHTML = '';
        trades.slice(0, 15).forEach(t => renderTradeRow(t, list));
    } catch (e) {
        console.error('Error fetching trades:', e);
    }
}

function handleNewTrade(t) {
    if (t.ticker === currentTicker) {
        const list = document.getElementById('tapeList');
        renderTradeRow(t, list, true);
        priceHistory.push(t.price);
        if (priceHistory.length > 20) priceHistory.shift();
        drawMiniChart();
    }
    refreshAccount();
    refreshPortfolio();
}

function renderTradeRow(t, container, prepend = false) {
    const row = document.createElement('div');
    row.className = 'tape-row';
    const timeStr = new Date(t.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    row.innerHTML = `
        <span style="color: var(--text-muted)">${timeStr}</span>
        <span class="${t.buyerId === currentTraderId ? 'text-green' : (t.sellerId === currentTraderId ? 'text-red' : '')}">
            ${t.buyerId} ➔ ${t.sellerId}
        </span>
        <span>${t.quantity} @ <strong>$${t.price.toFixed(2)}</strong></span>
    `;
    if (prepend && container.firstChild) {
        container.insertBefore(row, container.firstChild);
    } else {
        container.appendChild(row);
    }
}

// 4. Orders Management
async function refreshOrders() {
    try {
        const res = await fetch(`${API_BASE}/api/orders?traderId=${currentTraderId}`);
        const orders = await res.json();

        const openOrders = orders.filter(o => o.status === 'NEW' || o.status === 'PARTIALLY_FILLED');
        const executedOrders = orders.filter(o => o.status === 'FILLED');
        const otherOrders = orders.filter(o => o.status === 'CANCELLED' || o.status === 'REJECTED');

        document.getElementById('openCount').innerText = openOrders.length;
        document.getElementById('execCount').innerText = executedOrders.length;

        const container = document.getElementById('ordersListContainer');
        container.innerHTML = '';

        let targetList = openOrders;
        if (currentOrderSubTab === 'executed') targetList = executedOrders;
        else if (currentOrderSubTab === 'other') targetList = otherOrders;

        if (targetList.length === 0) {
            container.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 40px 0;">No ${currentOrderSubTab} orders.</div>`;
            return;
        }

        targetList.forEach(o => {
            const card = document.createElement('div');
            card.className = 'order-card';
            const isBuy = o.side === 'BUY';
            const isCancelable = (o.status === 'NEW' || o.status === 'PARTIALLY_FILLED');

            card.innerHTML = `
                <div class="order-card-top">
                    <div style="display:flex; align-items:center; gap:8px;">
                        <span class="order-side-badge ${isBuy ? 'buy' : 'sell'}">${o.side}</span>
                        <strong>${o.ticker}</strong>
                        <span class="order-type-tag text-mono">${o.type}</span>
                    </div>
                    <span class="text-mono" style="font-size:0.75rem; color:var(--text-muted);">${o.status}</span>
                </div>
                <div class="order-card-details text-mono">
                    <span>Price: $${o.price.toFixed(2)}</span>
                    <span>Filled: ${o.filledQuantity} / ${o.initialQuantity}</span>
                </div>
                ${isCancelable ? `<button class="btn-cancel-order" onclick="cancelOrder('${o.id}')">Cancel Order</button>` : ''}
            `;
            container.appendChild(card);
        });
    } catch (e) {
        console.error('Error fetching orders:', e);
    }
}

function switchOrderSubTab(tab, e) {
    currentOrderSubTab = tab;
    document.querySelectorAll('.sub-tab').forEach(b => b.classList.remove('active'));
    if (e && e.target) e.target.classList.add('active');
    refreshOrders();
}

async function cancelOrder(orderId) {
    try {
        const res = await fetch(`${API_BASE}/api/orders/${orderId}`, { method: 'DELETE' });
        const data = await res.json();
        if (data.success) {
            refreshOrders();
            refreshOrderBook();
        } else {
            alert('Cancel failed: ' + data.message);
        }
    } catch (e) {
        alert('Network error cancelling order');
    }
}

// 5. Portfolio & Accounts
async function refreshPortfolio() {
    try {
        const res = await fetch(`${API_BASE}/api/portfolio?traderId=${currentTraderId}`);
        const data = await res.json();

        const container = document.getElementById('positionsListContainer');
        container.innerHTML = '';

        if (!data.positions || data.positions.length === 0) {
            container.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px 0;">No active stock positions.</div>';
            return;
        }

        data.positions.forEach(p => {
            const card = document.createElement('div');
            card.className = 'pos-card';
            const isProfit = p.unrealizedPnL >= 0;

            card.innerHTML = `
                <div class="pos-left">
                    <div class="ticker">${p.ticker}</div>
                    <div class="shares text-mono">${p.shares} shares @ $${p.avgCost.toFixed(2)}</div>
                </div>
                <div class="pos-right text-mono">
                    <div class="pnl ${isProfit ? 'text-green' : 'text-red'}">
                        ${isProfit ? '+' : ''}$${p.unrealizedPnL.toFixed(2)}
                    </div>
                    <div class="curr-val">Current: $${p.currentPrice.toFixed(2)}</div>
                </div>
            `;
            container.appendChild(card);
        });
    } catch (e) {
        console.error('Error fetching portfolio:', e);
    }
}

async function refreshAccount() {
    try {
        const res = await fetch(`${API_BASE}/api/account?traderId=${currentTraderId}`);
        const acc = await res.json();

        document.getElementById('hdrCash').innerText = `$${acc.balance.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
        document.getElementById('hdrPnL').innerText = `$${acc.realizedPnL.toFixed(2)}`;
        document.getElementById('hdrPnL').className = `acc-val text-mono ${acc.realizedPnL >= 0 ? 'text-green' : 'text-red'}`;

        document.getElementById('pfCash').innerText = `$${acc.balance.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
        document.getElementById('pfRealizedPnL').innerText = `$${acc.realizedPnL.toFixed(2)}`;
        document.getElementById('pfRealizedPnL').className = `pf-value text-mono ${acc.realizedPnL >= 0 ? 'text-green' : 'text-red'}`;

        document.getElementById('fundsAvailable').innerText = `$${acc.balance.toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
        document.getElementById('exchangeRevenueDisplay').innerText = `$${acc.exchangeRevenue.toFixed(2)}`;
    } catch (e) {
        console.error('Error fetching account:', e);
    }
}

async function depositFunds() {
    const input = document.getElementById('depositAmount');
    const amount = parseFloat(input.value);
    if (!amount || amount <= 0) return;

    try {
        const res = await fetch(`${API_BASE}/api/account`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ traderId: currentTraderId, amount: amount })
        });
        const data = await res.json();
        if (data.success) {
            refreshAccount();
            input.value = '';
            alert(`Deposited $${amount.toFixed(2)} to ${currentTraderId}`);
        }
    } catch (e) {
        alert('Deposit error');
    }
}

// 6. Order Placement Modal
function openOrderSheet(side) {
    orderSide = side;
    setOrderSide(side);
    document.getElementById('orderSheetOverlay').classList.add('open');
    calculateEstimates();
}

function closeOrderSheet(e) {
    if (!e || e.target.id === 'orderSheetOverlay') {
        document.getElementById('orderSheetOverlay').classList.remove('open');
    }
}

function setOrderSide(side) {
    orderSide = side;
    const btnBuy = document.getElementById('toggleBuy');
    const btnSell = document.getElementById('toggleSell');
    const submitBtn = document.getElementById('btnSubmitOrder');

    if (side === 'BUY') {
        btnBuy.classList.add('active');
        btnSell.classList.remove('active');
        submitBtn.className = 'btn-submit-order buy';
        submitBtn.innerText = `PLACE BUY ${currentTicker}`;
        document.getElementById('sumTotalLabel').innerText = 'Total Required:';
    } else {
        btnSell.classList.add('active');
        btnBuy.classList.remove('active');
        submitBtn.className = 'btn-submit-order sell';
        submitBtn.innerText = `PLACE SELL ${currentTicker}`;
        document.getElementById('sumTotalLabel').innerText = 'Net Proceeds:';
    }
    calculateEstimates();
}

function setOrderType(type, e) {
    orderType = type;
    document.querySelectorAll('.type-pill').forEach(p => p.classList.remove('active'));
    if (e && e.target) e.target.classList.add('active');

    const priceInput = document.getElementById('orderPrice');

    if (type === 'MARKET') {
        priceInput.disabled = true;
        priceInput.value = '0';
    } else {
        priceInput.disabled = false;
        if (priceInput.value === '0') priceInput.value = '100.00';
    }
    calculateEstimates();
}

function addQty(n) {
    const input = document.getElementById('orderQty');
    input.value = (parseInt(input.value) || 0) + n;
    calculateEstimates();
}

function calculateEstimates() {
    const qty = parseInt(document.getElementById('orderQty').value) || 0;
    const price = parseFloat(document.getElementById('orderPrice').value) || 0;
    const tradeVal = qty * price;
    const fee = tradeVal * 0.001; // 0.1%

    document.getElementById('sumValue').innerText = `$${tradeVal.toFixed(2)}`;
    document.getElementById('sumFee').innerText = `$${fee.toFixed(2)}`;

    const total = orderSide === 'BUY' ? (tradeVal + fee) : (tradeVal - fee);
    document.getElementById('sumTotal').innerText = `$${total.toFixed(2)}`;
}

async function submitOrder() {
    const qty = parseInt(document.getElementById('orderQty').value) || 0;
    const price = parseFloat(document.getElementById('orderPrice').value) || 0;
    const errBox = document.getElementById('orderErrorMsg');
    errBox.classList.add('hidden');

    const payload = {
        traderId: currentTraderId,
        ticker: currentTicker,
        side: orderSide,
        price: price,
        quantity: qty,
        type: orderType
    };

    try {
        const res = await fetch(`${API_BASE}/api/orders`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const result = await res.json();

        if (result.success) {
            closeOrderSheet();
            refreshOrders();
            refreshOrderBook();
        } else {
            errBox.innerText = result.message || 'Order rejected';
            errBox.classList.remove('hidden');
        }
    } catch (e) {
        errBox.innerText = 'Network connection failed';
        errBox.classList.remove('hidden');
    }
}

// 7. Admin & Diagnostics
async function resumeTrading() {
    try {
        const res = await fetch(`${API_BASE}/api/admin/resume`, { method: 'POST' });
        const data = await res.json();
        alert(data.message || 'Trading resumed');
        toggleCircuitBanner(false, currentTicker);
        refreshWatchlist();
    } catch (e) {
        alert('Failed to resume trading');
    }
}

async function triggerConcurrencyTest() {
    try {
        const res = await fetch(`${API_BASE}/api/admin/test-concurrency`, { method: 'POST' });
        const data = await res.json();
        alert(data.message || 'Concurrent test executed');
    } catch (e) {
        alert('Concurrency test failed to start');
    }
}

function toggleCircuitBanner(isPaused, ticker) {
    const banner = document.getElementById('circuitBanner');
    if (isPaused) {
        document.getElementById('pausedTicker').innerText = ticker || 'AAPL';
        banner.classList.remove('hidden');
    } else {
        banner.classList.add('hidden');
    }
}

// 8. Navigation
function switchScreen(screenName) {
    document.querySelectorAll('.screen').forEach(s => s.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));

    const target = document.getElementById(`screen-${screenName}`);
    if (target) target.classList.add('active');

    const navIndices = { watchlist: 0, trade: 1, orders: 2, portfolio: 3, funds: 4 };
    const idx = navIndices[screenName];
    if (idx !== undefined) {
        document.querySelectorAll('.bottom-nav .nav-item')[idx].classList.add('active');
    }
}

// 9. Canvas Mini Chart
function initMiniChart() {
    drawMiniChart();
}

function drawMiniChart() {
    const canvas = document.getElementById('miniChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const w = canvas.width = canvas.parentElement.clientWidth - 16;
    const h = canvas.height = 110;

    ctx.clearRect(0, 0, w, h);

    if (priceHistory.length < 2) return;

    const min = Math.min(...priceHistory) * 0.999;
    const max = Math.max(...priceHistory) * 1.001;
    const range = (max - min) || 1;

    // Draw Gradient Area
    ctx.beginPath();
    ctx.moveTo(0, h);

    priceHistory.forEach((p, i) => {
        const x = (i / (priceHistory.length - 1)) * w;
        const y = h - ((p - min) / range) * (h - 20) - 10;
        ctx.lineTo(x, y);
    });

    ctx.lineTo(w, h);
    ctx.closePath();

    const gradient = ctx.createLinearGradient(0, 0, 0, h);
    gradient.addColorStop(0, 'rgba(0, 192, 135, 0.25)');
    gradient.addColorStop(1, 'rgba(0, 192, 135, 0.0)');
    ctx.fillStyle = gradient;
    ctx.fill();

    // Draw Price Line
    ctx.beginPath();
    priceHistory.forEach((p, i) => {
        const x = (i / (priceHistory.length - 1)) * w;
        const y = h - ((p - min) / range) * (h - 20) - 10;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
    });

    ctx.strokeStyle = '#00c087';
    ctx.lineWidth = 2.5;
    ctx.stroke();
}
