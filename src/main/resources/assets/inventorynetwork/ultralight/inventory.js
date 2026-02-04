// Solomon Inventory UI - Mock Data Demo

// ============================================
// MOCK DATA
// ============================================

const MOCK_ITEMS = [
  { id: 1, name: "Diamond", mc_id: "minecraft:diamond", qty: 1250, price: 850, base_price: 800, change: 5.2, target: 2000, supply: 65, demand: 82, pressure: 45 },
  { id: 2, name: "Emerald", mc_id: "minecraft:emerald", qty: 3200, price: 420, base_price: 450, change: -2.1, target: 3000, supply: 78, demand: 55, pressure: 30 },
  { id: 3, name: "Netherite Scrap", mc_id: "minecraft:netherite_scrap", qty: 89, price: 2500, base_price: 2200, change: 12.5, target: 500, supply: 15, demand: 95, pressure: 88 },
  { id: 4, name: "Gold Ingot", mc_id: "minecraft:gold_ingot", qty: 5600, price: 120, base_price: 115, change: 4.3, target: 5000, supply: 72, demand: 60, pressure: 35 },
  { id: 5, name: "Iron Ingot", mc_id: "minecraft:iron_ingot", qty: 12500, price: 45, base_price: 50, change: -1.8, target: 10000, supply: 85, demand: 45, pressure: 20 },
  { id: 6, name: "Lapis Lazuli", mc_id: "minecraft:lapis_lazuli", qty: 4200, price: 65, base_price: 60, change: 8.3, target: 4000, supply: 68, demand: 70, pressure: 50 },
  { id: 7, name: "Redstone", mc_id: "minecraft:redstone", qty: 8900, price: 35, base_price: 40, change: -3.5, target: 8000, supply: 90, demand: 40, pressure: 25 },
  { id: 8, name: "Coal", mc_id: "minecraft:coal", qty: 25000, price: 12, base_price: 15, change: -5.2, target: 15000, supply: 95, demand: 30, pressure: 10 },
  { id: 9, name: "Ender Pearl", mc_id: "minecraft:ender_pearl", qty: 320, price: 450, base_price: 400, change: 15.0, target: 1000, supply: 25, demand: 88, pressure: 75 },
  { id: 10, name: "Blaze Rod", mc_id: "minecraft:blaze_rod", qty: 180, price: 380, base_price: 350, change: 8.6, target: 500, supply: 30, demand: 85, pressure: 70 },
  { id: 11, name: "Quartz", mc_id: "minecraft:quartz", qty: 6500, price: 28, base_price: 30, change: -2.5, target: 5000, supply: 80, demand: 50, pressure: 40 },
  { id: 12, name: "Obsidian", mc_id: "minecraft:obsidian", qty: 1800, price: 95, base_price: 90, change: 5.5, target: 2000, supply: 55, demand: 65, pressure: 45 },
  { id: 13, name: "Glowstone Dust", mc_id: "minecraft:glowstone_dust", qty: 2400, price: 55, base_price: 50, change: 10.0, target: 3000, supply: 60, demand: 72, pressure: 55 },
  { id: 14, name: "Prismarine Shard", mc_id: "minecraft:prismarine_shard", qty: 890, price: 75, base_price: 70, change: 7.1, target: 1500, supply: 45, demand: 68, pressure: 60 },
  { id: 15, name: "Copper Ingot", mc_id: "minecraft:copper_ingot", qty: 9500, price: 22, base_price: 25, change: -4.0, target: 8000, supply: 88, demand: 35, pressure: 15 },
  { id: 16, name: "Amethyst Shard", mc_id: "minecraft:amethyst_shard", qty: 650, price: 180, base_price: 160, change: 12.5, target: 1000, supply: 40, demand: 78, pressure: 65 },
  { id: 17, name: "Echo Shard", mc_id: "minecraft:echo_shard", qty: 45, price: 3500, base_price: 3000, change: 16.7, target: 200, supply: 10, demand: 98, pressure: 92 },
  { id: 18, name: "Disc Fragment", mc_id: "minecraft:disc_fragment_5", qty: 28, price: 4200, base_price: 4000, change: 5.0, target: 100, supply: 8, demand: 95, pressure: 90 },
  { id: 19, name: "Bone", mc_id: "minecraft:bone", qty: 15000, price: 8, base_price: 10, change: -6.5, target: 10000, supply: 92, demand: 25, pressure: 12 },
  { id: 20, name: "String", mc_id: "minecraft:string", qty: 8500, price: 15, base_price: 18, change: -4.2, target: 6000, supply: 85, demand: 38, pressure: 18 },
  { id: 21, name: "Slimeball", mc_id: "minecraft:slime_ball", qty: 1200, price: 140, base_price: 130, change: 7.7, target: 2000, supply: 48, demand: 72, pressure: 58 },
  { id: 22, name: "Gunpowder", mc_id: "minecraft:gunpowder", qty: 3800, price: 65, base_price: 60, change: 8.3, target: 4000, supply: 62, demand: 68, pressure: 52 },
  { id: 23, name: "Phantom Membrane", mc_id: "minecraft:phantom_membrane", qty: 280, price: 320, base_price: 280, change: 14.3, target: 500, supply: 35, demand: 82, pressure: 72 },
  { id: 24, name: "Shulker Shell", mc_id: "minecraft:shulker_shell", qty: 95, price: 1800, base_price: 1600, change: 12.5, target: 300, supply: 20, demand: 90, pressure: 85 },
  { id: 25, name: "Nether Star", mc_id: "minecraft:nether_star", qty: 12, price: 15000, base_price: 14000, change: 7.1, target: 50, supply: 5, demand: 99, pressure: 95 },
  { id: 26, name: "Totem of Undying", mc_id: "minecraft:totem_of_undying", qty: 35, price: 8500, base_price: 8000, change: 6.3, target: 100, supply: 12, demand: 92, pressure: 88 },
  { id: 27, name: "Golden Apple", mc_id: "minecraft:golden_apple", qty: 180, price: 950, base_price: 900, change: 5.6, target: 300, supply: 42, demand: 75, pressure: 62 },
  { id: 28, name: "Enchanted Book", mc_id: "minecraft:enchanted_book", qty: 520, price: 650, base_price: 600, change: 8.3, target: 800, supply: 50, demand: 70, pressure: 55 }
];

const MOCK_TRADES = [
  { user: "Steve123", direction: "GAINED", qty: 64, time: "2 hours ago" },
  { user: "Alex_Builder", direction: "GIVEN", qty: 128, time: "5 hours ago" },
  { user: "DiamondMiner42", direction: "GAINED", qty: 32, time: "8 hours ago" },
  { user: "CraftMaster", direction: "GIVEN", qty: 256, time: "12 hours ago" },
  { user: "BlockBreaker99", direction: "GAINED", qty: 16, time: "1 day ago" },
  { user: "RedstoneWiz", direction: "GIVEN", qty: 512, time: "1 day ago" },
  { user: "NetherKing", direction: "GAINED", qty: 48, time: "2 days ago" },
  { user: "EnderDragon", direction: "GIVEN", qty: 96, time: "3 days ago" }
];

// ============================================
// STATE
// ============================================

let currentItems = [...MOCK_ITEMS];
let selectedItem = null;
let priceChart = null;
let searchQuery = '';
let sortOption = 'name-asc';
let locationFilter = 'all';

// ============================================
// UTILITY FUNCTIONS
// ============================================

function shortQty(n) {
  if (n >= 1e9) return (n / 1e9).toFixed(1) + 'b';
  if (n >= 1e6) return (n / 1e6).toFixed(1) + 'm';
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'k';
  return n.toString();
}

function formatPrice(price) {
  if (price >= 1000) return (price / 1000).toFixed(1) + 'k';
  return price.toString();
}

function getStockClass(item) {
  const pct = (item.qty / item.target) * 100;
  if (pct < 30) return 'low-stock';
  if (pct > 150) return 'overstock';
  return '';
}

function getStockBarClass(item) {
  const pct = (item.qty / item.target) * 100;
  if (pct < 30) return 'low';
  if (pct < 70) return 'medium';
  if (pct <= 120) return 'normal';
  return 'over';
}

function getItemIconUrl(mc_id) {
  const itemName = mc_id.replace('minecraft:', '');
  return `https://mc.nerothe.com/img/1.20.1/${itemName}.png`;
}

function generatePriceHistory(basePrice, days = 7) {
  const history = [];
  let price = basePrice * (0.85 + Math.random() * 0.15);

  for (let i = days - 1; i >= 0; i--) {
    const date = new Date();
    date.setDate(date.getDate() - i);
    history.push({
      date: date.toISOString().split('T')[0],
      price: Math.round(price)
    });
    price = price * (0.95 + Math.random() * 0.1);
  }

  return history;
}

// ============================================
// RENDER FUNCTIONS
// ============================================

function renderGrid(items) {
  const grid = document.getElementById('grid');

  if (items.length === 0) {
    grid.innerHTML = '<div class="empty-state">No items found</div>';
    return;
  }

  grid.innerHTML = items.map(item => `
    <div class="inventory-tile ${getStockClass(item)}" onclick="openDrawer(${item.id})">
      <img class="item-icon" src="${getItemIconUrl(item.mc_id)}" alt="${item.name}" onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22><rect fill=%22%231a2552%22 width=%2232%22 height=%2232%22/><text x=%2216%22 y=%2220%22 text-anchor=%22middle%22 fill=%22%23f8d038%22 font-size=%2212%22>?</text></svg>'">
      <span class="qty-badge">${shortQty(item.qty)}</span>
      <span class="price-dot ${item.change >= 0 ? 'up' : 'down'}"></span>
    </div>
  `).join('');
}

function renderDrawer(item) {
  document.getElementById('detail-icon').src = getItemIconUrl(item.mc_id);
  document.getElementById('detail-name').textContent = item.name;
  document.getElementById('detail-mcid').textContent = item.mc_id;

  // Price
  document.getElementById('detail-price').textContent = `${formatPrice(item.price)} diamonds`;
  const changeEl = document.getElementById('detail-change');
  const arrowEl = document.getElementById('detail-arrow');
  const changeValueEl = document.getElementById('detail-change-value');

  changeEl.className = 'price-change ' + (item.change >= 0 ? 'up' : 'down');
  arrowEl.textContent = item.change >= 0 ? '▲' : '▼';
  changeValueEl.textContent = `${Math.abs(item.change).toFixed(1)}% (24h)`;

  document.getElementById('detail-base-price').textContent = `Base: ${item.base_price} diamonds`;

  // Stock
  const stockPct = Math.round((item.qty / item.target) * 100);
  document.getElementById('detail-stock-text').textContent = `${shortQty(item.qty)} / ${shortQty(item.target)} (${stockPct}%)`;

  const stockBar = document.getElementById('detail-stock-bar');
  stockBar.className = 'stock-bar ' + getStockBarClass(item);
  stockBar.style.width = Math.min(100, stockPct) + '%';

  // Market indicators
  document.getElementById('detail-supply').textContent = item.supply;
  document.getElementById('detail-demand').textContent = item.demand;
  document.getElementById('detail-pressure').textContent = item.pressure;

  // Trades
  const tradesEl = document.getElementById('detail-trades');
  tradesEl.innerHTML = MOCK_TRADES.slice(0, 5).map(trade => `
    <div class="trade-item">
      <span class="trade-user">${trade.user}</span>
      <span class="trade-direction ${trade.direction === 'GAINED' ? 'buy' : 'sell'}">${trade.direction === 'GAINED' ? 'Buy' : 'Sell'}</span>
      <span class="trade-qty">x${trade.qty}</span>
      <span class="trade-time">${trade.time}</span>
    </div>
  `).join('');

  // Chart
  renderPriceChart(item);
}

function renderPriceChart(item) {
  const ctx = document.getElementById('price-chart').getContext('2d');
  const history = generatePriceHistory(item.base_price);

  if (priceChart) {
    priceChart.destroy();
  }

  priceChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: history.map(h => h.date.slice(5)),
      datasets: [{
        data: history.map(h => h.price),
        borderColor: '#f8d038',
        backgroundColor: 'rgba(248, 208, 56, 0.1)',
        fill: true,
        tension: 0.3,
        pointRadius: 3,
        pointHoverRadius: 6,
        pointBackgroundColor: '#f8d038',
        pointBorderColor: '#1a2552',
        pointBorderWidth: 2
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#1a2552',
          titleColor: '#e0f0f0',
          bodyColor: '#f8d038',
          borderColor: 'rgba(255,255,255,0.12)',
          borderWidth: 1,
          padding: 10,
          callbacks: {
            label: ctx => `${ctx.parsed.y} diamonds`
          }
        }
      },
      scales: {
        x: {
          grid: { color: 'rgba(255,255,255,0.05)' },
          ticks: { color: '#9fb0d0', font: { size: 10 } }
        },
        y: {
          grid: { color: 'rgba(255,255,255,0.05)' },
          ticks: {
            color: '#9fb0d0',
            font: { size: 10 },
            callback: v => formatPrice(v)
          }
        }
      }
    }
  });
}

// ============================================
// EVENT HANDLERS
// ============================================

function handleSearch(query) {
  searchQuery = query.toLowerCase();
  applyFilters();
}

function handleSort(option) {
  sortOption = option;
  applyFilters();
}

function handleLocationFilter(location) {
  locationFilter = location;
  applyFilters();
}

function applyFilters() {
  let filtered = [...MOCK_ITEMS];

  // Search filter
  if (searchQuery) {
    filtered = filtered.filter(item =>
      item.name.toLowerCase().includes(searchQuery) ||
      item.mc_id.toLowerCase().includes(searchQuery)
    );
  }

  // Sort
  switch (sortOption) {
    case 'name-asc':
      filtered.sort((a, b) => a.name.localeCompare(b.name));
      break;
    case 'name-desc':
      filtered.sort((a, b) => b.name.localeCompare(a.name));
      break;
    case 'qty-high':
      filtered.sort((a, b) => b.qty - a.qty);
      break;
    case 'qty-low':
      filtered.sort((a, b) => a.qty - b.qty);
      break;
    case 'price-high':
      filtered.sort((a, b) => b.price - a.price);
      break;
    case 'price-low':
      filtered.sort((a, b) => a.price - b.price);
      break;
  }

  currentItems = filtered;
  renderGrid(currentItems);
}

function openDrawer(itemId) {
  selectedItem = MOCK_ITEMS.find(i => i.id === itemId);
  if (!selectedItem) return;

  renderDrawer(selectedItem);
  document.getElementById('drawer').classList.add('open');
  document.getElementById('backdrop').classList.add('open');
}

function closeDrawer() {
  document.getElementById('drawer').classList.remove('open');
  document.getElementById('backdrop').classList.remove('open');
  selectedItem = null;
}

function closeOverlay() {
  // Called when X button in header is clicked
  // This will be wired to Java to close the Ultralight overlay
  if (window.SolomonBridge && window.SolomonBridge.closeUI) {
    window.SolomonBridge.closeUI();
  }
}

function handleIncreaseTarget() {
  if (selectedItem) {
    console.log('Increase target for:', selectedItem.name);
    // Will be wired to Java bridge
  }
}

function handleDecreaseTarget() {
  if (selectedItem) {
    console.log('Decrease target for:', selectedItem.name);
    // Will be wired to Java bridge
  }
}

// ============================================
// JAVA BRIDGE (will be populated by UltralightJSBridge)
// ============================================

window.SolomonBridge = window.SolomonBridge || {
  closeUI: function() { console.log('closeUI not bound'); },
  refreshData: function() { console.log('refreshData not bound'); },
  highlightChest: function(x, y, z) { console.log('highlightChest not bound'); },

  // Called from Java to update data
  updateItems: function(jsonData) {
    try {
      const items = JSON.parse(jsonData);
      MOCK_ITEMS.length = 0;
      MOCK_ITEMS.push(...items);
      applyFilters();
    } catch (e) {
      console.error('Failed to parse items:', e);
    }
  }
};

// Function called from Java via executeScript
function updateFromJava(jsonData) {
  SolomonBridge.updateItems(jsonData);
}

// Lifecycle hooks called from Java
function onOverlayOpen() {
  console.log('Overlay opened');
  // Could refresh data here
}

function onOverlayClose() {
  console.log('Overlay closed');
  closeDrawer();
}

// ============================================
// INITIALIZATION
// ============================================

document.addEventListener('DOMContentLoaded', function() {
  applyFilters();
});

// If no DOMContentLoaded (Ultralight might load differently)
if (document.readyState === 'complete' || document.readyState === 'interactive') {
  applyFilters();
}
