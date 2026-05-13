// POS.jsx — Verkaufsoberfläche (Produkte + Warenkorb)
const { useState, useMemo } = React;

function POS({categories, cart, setCart, onCheckout}){
  const [activeCat, setActiveCat] = useState(categories[0]?.id);
  const cat = categories.find(c=>c.id===activeCat) || categories[0];

  const addItem = (p) => {
    setCart(prev => {
      const i = prev.findIndex(x=>x.id===p.id);
      if(i>=0){ const cp=[...prev]; cp[i]={...cp[i],qty:cp[i].qty+1}; return cp; }
      return [...prev, {id:p.id, name:p.name, price:p.price, color:p.color, qty:1}];
    });
  };
  const inc = id => setCart(p=>p.map(x=>x.id===id?{...x,qty:x.qty+1}:x));
  const dec = id => setCart(p=>p.flatMap(x=>x.id===id?(x.qty>1?[{...x,qty:x.qty-1}]:[]):[x]));
  const rem = id => setCart(p=>p.filter(x=>x.id!==id));

  const total = cart.reduce((s,x)=>s+x.price*x.qty,0);
  const totalQty = cart.reduce((s,x)=>s+x.qty,0);

  const cartQtyMap = useMemo(()=>{
    const m = {}; cart.forEach(c=>m[c.id]=c.qty); return m;
  },[cart]);

  return (
    <div className="pos">
      {/* LINKS: Warenkorb */}
      <div className="panel cart">
        <div className="panel-h">
          <h2>Warenkorb</h2>
          <span className="sub">{totalQty} Artikel</span>
          {cart.length>0 && (
            <button className="btn ghost"
                    style={{marginLeft:'auto',padding:'4px 8px',fontSize:12.5}}
                    onClick={()=>setCart([])}>Leeren</button>
          )}
        </div>

        <div className="cart-list">
          {cart.length===0 ? (
            <div className="cart-empty">
              <div className="ico">🧁</div>
              Tippe rechts auf ein Produkt,<br/>
              um es zum Warenkorb hinzuzufügen.
            </div>
          ) : cart.map(c=>(
            <div key={c.id} className="cart-row">
              <div>
                <div className="nm">{c.name}</div>
                <div className="pr">{EUR(c.price)} / Stück</div>
              </div>
              <div className="qty">
                <button onClick={()=>dec(c.id)} aria-label="weniger">−</button>
                <span className="n">{c.qty}</span>
                <button onClick={()=>inc(c.id)} aria-label="mehr">+</button>
              </div>
              <div className="line-total">{EUR(c.price*c.qty)}</div>
              <button className="x" onClick={()=>rem(c.id)} title="Entfernen">✕</button>
            </div>
          ))}
        </div>

        <div className="cart-foot">
          <div className="tot-row">
            <span>Zwischensumme</span><span className="v">{EUR(total)}</span>
          </div>
          <div className="tot-row">
            <span>Artikel</span><span className="v">{totalQty}</span>
          </div>
          <div className="tot-row grand">
            <span>Summe</span><span className="v">{EUR(total)}</span>
          </div>

          <button className="sum-btn" disabled={cart.length===0} onClick={()=>onCheckout(total)}>
            <span>Summe & Bezahlen</span>
            <span className="arrow">→</span>
          </button>
        </div>
      </div>

      {/* RECHTS: Produkte + Kategorien unten */}
      <div className="panel">
        {cat && cat.products.length>0 ? (
          <div className="grid">
            {cat.products.map(p=>(
              <button key={p.id} className={'product '+colorCls(p.color)} onClick={()=>addItem(p)}>
                {cartQtyMap[p.id] ? <span className="badge">×{cartQtyMap[p.id]}</span> : null}
                <div className="name">{p.name}</div>
                <div className="price">{EUR(p.price)}</div>
              </button>
            ))}
          </div>
        ) : (
          <div className="empty-state">
            Keine Produkte in dieser Kategorie.<br/>
            <span style={{fontSize:12,opacity:.7}}>Im Admin-Panel hinzufügen.</span>
          </div>
        )}
        <div className="cat-bar cat-bar-bottom">
          {categories.map(c=>(
            <button key={c.id}
                    className={'cat-pill '+(activeCat===c.id?'active':'')}
                    onClick={()=>setActiveCat(c.id)}>
              <span className={'dot '+swatchCls(c.color)}></span>
              <span>{c.name}</span>
              <span className="ct">{c.products.length}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

window.POS = POS;