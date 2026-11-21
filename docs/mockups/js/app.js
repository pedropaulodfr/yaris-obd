/* ==========================================================================
   Galeria dos mockups — Yaris OBD
   Monta os 12 cartões, escala cada palco 1920×720 para o tamanho do cartão,
   mantém um único laço de animação e abre o visualizador em tela cheia.
   ========================================================================== */
(function () {
  'use strict';

  const grid = document.getElementById('grid');
  const chips = document.getElementById('chips');
  const viewer = document.getElementById('viewer');
  const vframe = document.getElementById('vframe');
  const vname = document.getElementById('vname');
  const vdesc = document.getElementById('vdesc');
  const vindex = document.getElementById('vindex');
  const selScenario = document.getElementById('scenario');
  const btnPause = document.getElementById('pause');

  const cards = [];
  const frameOwner = new Map();

  let paused = false;
  let viewerInst = null;
  let current = -1;

  // ------------------------------------------------------------------------
  // Montagem e escala
  // ------------------------------------------------------------------------

  function mount(layoutId, container) {
    const tpl = document.getElementById('tpl-' + layoutId);
    if (!tpl) {
      console.error('Template não encontrado: tpl-' + layoutId);
      return null;
    }
    const node = tpl.content.firstElementChild.cloneNode(true);
    container.appendChild(node);

    const api = Screens[layoutId].mount(node);
    const inst = { id: layoutId, el: node, container, api };
    frameOwner.set(container, inst);
    syncScale(inst);
    return inst;
  }

  function syncScale(inst) {
    if (!inst || !inst.container) return;
    const width = inst.container.clientWidth;
    if (!width) return;
    inst.el.style.transform = 'scale(' + (width / 1920) + ')';
  }

  const ro = new ResizeObserver((entries) => {
    for (const entry of entries) {
      const inst = frameOwner.get(entry.target);
      if (inst && entry.target.clientWidth) syncScale(inst);
    }
  });

  // ------------------------------------------------------------------------
  // Cartões da galeria
  // ------------------------------------------------------------------------

  LAYOUTS.forEach((layout, index) => {
    const card = document.createElement('article');
    card.className = 'card';
    card.id = 'card-' + layout.id;

    const head = document.createElement('div');
    head.className = 'card-head';
    head.innerHTML =
      '<div class="card-title">' +
      '<span class="num">' + String(index + 1).padStart(2, '0') + '</span>' +
      '<h2>' + layout.name + '</h2>' +
      '<span class="tag">' + layout.tag + '</span>' +
      '</div>' +
      '<p>' + layout.desc + '</p>';

    const frame = document.createElement('div');
    frame.className = 'frame';
    frame.setAttribute('role', 'button');
    frame.setAttribute('tabindex', '0');
    frame.setAttribute('aria-label', 'Ampliar ' + layout.name);

    const open = document.createElement('button');
    open.type = 'button';
    open.className = 'card-open';
    open.textContent = 'Ampliar ⤢';

    card.append(head, frame, open);
    grid.appendChild(card);

    const inst = mount(layout.id, frame);
    if (inst) {
      inst.inView = true;
      inst.index = index;
      cards.push(inst);
      ro.observe(frame);
    }

    const activate = () => openViewer(index);
    open.addEventListener('click', (event) => {
      event.stopPropagation();
      activate();
    });
    frame.addEventListener('click', activate);
    frame.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        activate();
      }
    });
  });

  // ------------------------------------------------------------------------
  // Atalhos no topo
  // ------------------------------------------------------------------------

  LAYOUTS.forEach((layout, index) => {
    const chip = document.createElement('button');
    chip.type = 'button';
    chip.className = 'chip';
    chip.innerHTML = '<b>' + String(index + 1).padStart(2, '0') + '</b>' + layout.name;
    chip.addEventListener('click', () => openViewer(index));
    chips.appendChild(chip);
  });

  // ------------------------------------------------------------------------
  // Visualizador em tela cheia
  // ------------------------------------------------------------------------

  function openViewer(index) {
    const layout = LAYOUTS[index];
    current = index;

    vname.textContent = layout.name;
    vdesc.textContent = layout.desc;
    vindex.textContent = (index + 1) + ' / ' + LAYOUTS.length;

    vframe.innerHTML = '';
    viewerInst = mount(layout.id, vframe);
    if (viewerInst) {
      viewerInst.inView = true;
      ro.observe(vframe);
    }

    viewer.hidden = false;
    document.body.style.overflow = 'hidden';
    setHash(layout.id);
    syncScale(viewerInst);
  }

  function setHash(id) {
    try {
      history.replaceState(null, '', '#' + id);
    } catch (error) {
      // file:// pode recusar replaceState; o hash continua funcionando.
      location.hash = id;
    }
  }

  function closeViewer() {
    viewer.hidden = true;
    vframe.innerHTML = '';
    viewerInst = null;
    current = -1;
    document.body.style.overflow = '';
    ro.unobserve(vframe);
    try {
      history.replaceState(null, '', location.pathname + location.search);
    } catch (error) {
      /* ignora em file:// */
    }
  }

  function step(delta) {
    if (current < 0) return;
    openViewer((current + delta + LAYOUTS.length) % LAYOUTS.length);
  }

  viewer.querySelectorAll('[data-nav]').forEach((btn) => {
    btn.addEventListener('click', () => step(btn.dataset.nav === 'next' ? 1 : -1));
  });
  viewer.querySelector('[data-close]').addEventListener('click', closeViewer);

  window.addEventListener('keydown', (event) => {
    if (viewer.hidden) return;
    if (event.key === 'Escape') closeViewer();
    else if (event.key === 'ArrowRight') step(1);
    else if (event.key === 'ArrowLeft') step(-1);
  });

  // ------------------------------------------------------------------------
  // Controles de simulação
  // ------------------------------------------------------------------------

  Object.entries(SIM.SCENARIOS).forEach(([id, scenario]) => {
    const option = document.createElement('option');
    option.value = id;
    option.textContent = scenario.label;
    selScenario.appendChild(option);
  });
  selScenario.value = SIM.state.scenario;
  selScenario.addEventListener('change', () => SIM.setScenario(selScenario.value));

  btnPause.addEventListener('click', () => {
    paused = !paused;
    btnPause.textContent = paused ? 'Continuar' : 'Pausar';
    btnPause.setAttribute('aria-pressed', String(paused));
  });

  // ------------------------------------------------------------------------
  // Visibilidade: só atualiza o que está na tela
  // ------------------------------------------------------------------------

  const io = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const inst = frameOwner.get(entry.target);
        if (inst) inst.inView = entry.isIntersecting;
      }
    },
    { rootMargin: '300px' },
  );

  cards.forEach((card) => io.observe(card.container));

  // ------------------------------------------------------------------------
  // Laço principal
  // ------------------------------------------------------------------------

  let last = performance.now();

  function loop(now) {
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;

    if (!paused) SIM.update(dt);

    if (!viewerInst) {
      for (const card of cards) {
        if (card.inView) card.api.update(SIM.state);
      }
    }
    if (viewerInst) viewerInst.api.update(SIM.state);

    requestAnimationFrame(loop);
  }

  requestAnimationFrame(loop);

  // ------------------------------------------------------------------------
  // Parâmetros de link: ?t=24 avança a simulação, ?s=sport escolhe cenário.
  // Serve para revisar um layout já em movimento, sem esperar.
  // ------------------------------------------------------------------------

  const params = new URLSearchParams(location.search);

  const forced = params.get('s');
  if (forced && SIM.SCENARIOS[forced]) {
    SIM.setScenario(forced);
    selScenario.value = forced;
  }

  const warm = Number(params.get('t'));
  if (warm > 0) {
    const steps = Math.min(7200, Math.round(warm * 60));
    for (let i = 0; i < steps; i++) SIM.update(1 / 60);
  }

  // Link direto: #radar abre aquele layout.
  const hash = location.hash.replace('#', '');
  if (hash) {
    const index = LAYOUTS.findIndex((layout) => layout.id === hash);
    if (index >= 0) openViewer(index);
  }
})();
