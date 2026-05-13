// App.jsx — root + state
const { useState, useEffect } = React;

function App(){
  const persisted = loadState() || {};
  const [user, setUser] = useState(persisted.user || null);
  const [recentUsers, setRecentUsers] = useState(persisted.recentUsers || []);
  const [categories, setCategories] = useState(persisted.categories || DEFAULT_CATS);
  const [sales, setSales] = useState(persisted.sales || []);
  const [cart, setCart] = useState(persisted.cart || []);
  const [openingCash, setOpeningCash] = useState(persisted.openingCash || '50,00');
  const [tab, setTab] = useState(persisted.tab || 'pos'); // pos | report | admin
  const [payOpen, setPayOpen] = useState(false);
  const [toast, setToast] = useState(null);
  const [clock, setClock] = useState(new Date());
  const [theme, setTheme] = useState(persisted.theme || 'default'); // default | farm

  // Persist
  useEffect(()=>{
    saveState({user, recentUsers, categories, sales, cart, openingCash, tab, theme});
  }, [user, recentUsers, categories, sales, cart, openingCash, tab, theme]);

  // Apply theme to <body> so all panels switch together
  useEffect(()=>{
    if(theme === 'farm') document.body.setAttribute('data-theme','farm');
    else document.body.removeAttribute('data-theme');
  }, [theme]);

  // Clock
  useEffect(()=>{
    const id = setInterval(()=>setClock(new Date()), 1000*15);
    return ()=>clearInterval(id);
  },[]);

  // Toast helper
  const showToast = (msg) => {
    setToast(msg);
    setTimeout(()=>setToast(null), 2200);
  };

  const onLogin = (u) => {
    setUser(u);
    setRecentUsers(prev => {
      const filtered = prev.filter(p=>p.name!==u.name);
      return [{name:u.name, klasse:u.klasse, role:u.role}, ...filtered].slice(0,5);
    });
    setTab(u.role==='admin' ? 'admin' : 'pos');
  };

  const onLogout = () => {
    setUser(null);
    setCart([]);
  };

  const onCheckout = () => {
    if(cart.length===0) return;
    setPayOpen(true);
  };

  const onPaid = (info) => {
    const sale = {
      id: 'b'+Date.now(),
      ts: Date.now(),
      method: info.method,
      total: info.total,
      given: info.given,
      change: info.change,
      items: cart.map(c=>({...c})),
      by: user?.name || ''
    };
    setSales(s=>[sale, ...s]);
    setCart([]);
    showToast(`Verkauf gebucht · ${EUR(info.total)} ${info.method}`);
  };

  const onCloseShift = (summary) => {
    const ok = confirm(
      `Schicht abschließen?\n\n` +
      `Umsatz: ${EUR(summary.totals.totalSales)}\n` +
      `Differenz: ${summary.diff>=0?'+':''}${EUR(summary.diff)}\n\n` +
      `Die Verkaufsdaten werden archiviert und die Kasse zurückgesetzt.`
    );
    if(!ok) return;
    // archive (in real app we'd persist; here just clear)
    setSales([]);
    setCart([]);
    setUser(null);
    showToast('Schicht abgeschlossen · Bericht archiviert');
  };

  if(!user){
    return <Login onLogin={onLogin} recent={recentUsers} />;
  }

  const isAdmin = user.role==='admin';

  return (
    <div className="app">
      <div className="topbar">
        <div className="brand">
          <div className="brand-mark">K</div>
          <div className="brand-text">
            <div className="t1">Schulkasse</div>
            <div className="t2">Kuchenverkauf</div>
          </div>
        </div>

        <div className="tabs">
          <button className={'tab '+(tab==='pos'?'active':'')} onClick={()=>setTab('pos')}>
            {theme==='farm' ? '› POS' : 'Kasse'}
          </button>
          <button className={'tab '+(tab==='report'?'active':'')} onClick={()=>setTab('report')}>
            {theme==='farm' ? '› SHIFT' : 'Abschluss'}
          </button>
          {isAdmin && (
            <button className={'tab '+(tab==='admin'?'active':'')} onClick={()=>setTab('admin')}>
              {theme==='farm' ? '› ADMIN' : 'Admin'}
            </button>
          )}
        </div>

        <div className="right">
          <button className="theme-toggle"
                  onClick={()=>setTheme(t=>t==='farm'?'default':'farm')}
                  title={theme==='farm'?'Standard-Ansicht':'Farm-Modus (Terminal)'}>
            <span className="dot"></span>
            {theme==='farm' ? 'FARM' : 'Farm-Modus'}
          </button>
          <span className="clock">{clock.toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'})}</span>
          <div className="user-pill">
            <div className="avatar">{(user.name||'?')[0].toUpperCase()}</div>
            <span style={{fontSize:13,fontWeight:550,color:'var(--ink)'}}>{user.name}</span>
            {user.klasse && <span style={{color:'var(--ink-3)',fontSize:12}}>{user.klasse}</span>}
          </div>
          <button className="btn ghost" onClick={onLogout} title="Abmelden" style={{padding:'6px 10px'}}>Abmelden</button>
        </div>
      </div>

      {tab==='pos' && (
        <POS categories={categories} cart={cart} setCart={setCart} onCheckout={onCheckout} />
      )}
      {tab==='report' && (
        <Report sales={sales} openingCash={openingCash}
                onSetOpening={setOpeningCash}
                onCloseShift={onCloseShift}
                user={user} shiftStartedAt={user.startedAt} />
      )}
      {tab==='admin' && isAdmin && (
        <Admin categories={categories} setCategories={setCategories} />
      )}

      {payOpen && (
        <PayModal total={cart.reduce((s,x)=>s+x.price*x.qty,0)}
                  items={cart}
                  onClose={()=>setPayOpen(false)}
                  onComplete={onPaid} />
      )}

      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);