// Admin.jsx — Knöpfe/Kategorien/Produkte verwalten
const { useState } = React;

function Admin({categories, setCategories}){
  const [activeId, setActiveId] = useState(categories[0]?.id);
  const [newCat, setNewCat] = useState('');
  const cat = categories.find(c=>c.id===activeId) || categories[0];

  const updateCat = (id, patch) =>
    setCategories(cs => cs.map(c => c.id===id ? {...c, ...patch} : c));

  const updateProd = (catId, pId, patch) =>
    setCategories(cs => cs.map(c =>
      c.id!==catId ? c : {...c, products: c.products.map(p => p.id===pId ? {...p, ...patch} : p)}
    ));

  const addProd = (catId) => {
    setCategories(cs => cs.map(c =>
      c.id!==catId ? c : {...c, products:[...c.products, {id:uid(), name:'Neues Produkt', price:1.00, color:c.color}]}
    ));
  };

  const delProd = (catId, pId) => {
    setCategories(cs => cs.map(c =>
      c.id!==catId ? c : {...c, products: c.products.filter(p=>p.id!==pId)}
    ));
  };

  const addCat = () => {
    const name = newCat.trim();
    if(!name) return;
    const id = 'c'+Math.random().toString(36).slice(2,7);
    const usedColors = categories.map(c=>c.color);
    const color = (COLORS.find(c=>!usedColors.includes(c.id)) || COLORS[0]).id;
    setCategories(cs => [...cs, {id, name, color, products:[]}]);
    setNewCat('');
    setActiveId(id);
  };

  const delCat = (id) => {
    if(!confirm('Kategorie wirklich löschen? Alle enthaltenen Produkte gehen verloren.')) return;
    setCategories(cs => {
      const next = cs.filter(c=>c.id!==id);
      if(activeId===id) setActiveId(next[0]?.id);
      return next;
    });
  };

  return (
    <div className="admin">
      <div className="side">
        <h4>Kategorien</h4>
        {categories.map(c=>(
          <div key={c.id}
               className={'cat-item '+(activeId===c.id?'active':'')}
               onClick={()=>setActiveId(c.id)}>
            <span className={'swatch '+swatchCls(c.color)}></span>
            <input className="nm" value={c.name}
                   onChange={e=>updateCat(c.id,{name:e.target.value})}
                   onClick={e=>e.stopPropagation()} />
            <span className="ct">{c.products.length}</span>
            <button className="del-x"
                    onClick={e=>{e.stopPropagation(); delCat(c.id);}}
                    title="Kategorie löschen">✕</button>
          </div>
        ))}

        <div className="add-cat">
          <input value={newCat} onChange={e=>setNewCat(e.target.value)}
                 onKeyDown={e=>e.key==='Enter'&&addCat()}
                 placeholder="Neue Kategorie…" />
          <button className="btn" onClick={addCat}>+</button>
        </div>
      </div>

      <div className="main">
        {!cat ? (
          <div className="empty-state">Keine Kategorie ausgewählt.</div>
        ) : (
          <>
            <div className="main-h">
              <h3>{cat.name}</h3>
              <span className="meta">{cat.products.length} Produkte</span>
              <div className="swatch-pick" title="Kategorie-Farbe">
                {COLORS.map(c=>(
                  <button key={c.id}
                          className={'s '+c.sw+(cat.color===c.id?' sel':'')}
                          onClick={()=>updateCat(cat.id,{color:c.id})}
                          title={c.label}></button>
                ))}
              </div>
            </div>

            <div className="prod-list">
              {cat.products.length===0 ? (
                <div className="empty-state" style={{padding:'40px 20px'}}>
                  Noch keine Produkte. Mit „+ Produkt“ hinzufügen.
                </div>
              ) : cat.products.map(p=>(
                <div key={p.id} className="prod-row">
                  <span className="drag" title="Ziehen zum Umsortieren">⋮⋮</span>
                  <input className="nm-i" value={p.name}
                         onChange={e=>updateProd(cat.id,p.id,{name:e.target.value})} />
                  <div className="swatch-pick">
                    {COLORS.map(c=>(
                      <button key={c.id}
                              className={'s '+c.sw+(p.color===c.id?' sel':'')}
                              onClick={()=>updateProd(cat.id,p.id,{color:c.id})}
                              title={c.label}></button>
                    ))}
                  </div>
                  <input className="pr-i" type="number" step="0.10" min="0"
                         value={p.price}
                         onChange={e=>updateProd(cat.id,p.id,{price:parseFloat(e.target.value)||0})} />
                  <button className="del" onClick={()=>delProd(cat.id,p.id)}>✕</button>
                </div>
              ))}
            </div>

            <div className="add-prod">
              <button className="btn secondary" onClick={()=>addProd(cat.id)}>+ Produkt hinzufügen</button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

window.Admin = Admin;