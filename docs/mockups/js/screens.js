/* ==========================================================================
   Layouts dos mockups — Yaris OBD
   --------------------------------------------------------------------------
   Cada layout expõe `Screens.<id>.mount(root)` e devolve `{ update(state) }`.
   O `root` é o elemento `.stage`; os valores são ligados por `data-bind="..."`.

   Convenções que vieram das regras do projeto:
   - número indisponível é sempre "—", nunca 0;
   - todo valor colorido tem rótulo e número ao lado;
   - dígitos tabulares (font-variant-numeric no CSS).
   ========================================================================== */
(function (global) {
  'use strict';

  const NS = 'http://www.w3.org/2000/svg';
  const DASH = '—';

  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

  const U = {
    int(v, dash = DASH) {
      return v === null || v === undefined || !isFinite(v) ? dash : String(Math.round(v));
    },
    dec(v, digits = 1, dash = DASH) {
      return v === null || v === undefined || !isFinite(v) ? dash : v.toFixed(digits);
    },
    set(el, value) {
      if (el && el.textContent !== value) el.textContent = value;
    },
    css(el, prop, value) {
      if (el && el.style.getPropertyValue(prop) !== value) el.style.setProperty(prop, value);
    },
    width(el, fraction) {
      const pct = (clamp(fraction, 0, 1) * 100).toFixed(2) + '%';
      if (el && el.style.width !== pct) el.style.width = pct;
    },
    attr(el, name, value) {
      if (el && el.getAttribute(name) !== value) el.setAttribute(name, value);
    },
  };

  function scope(root) {
    const cache = Object.create(null);
    const all = (name) => cache[name] || (cache[name] = Array.from(root.querySelectorAll('[data-bind="' + name + '"]')));
    return {
      root,
      q: (sel) => root.querySelector(sel),
      qa: (sel) => Array.from(root.querySelectorAll(sel)),
      all,
      set(name, value) {
        const list = all(name);
        for (let i = 0; i < list.length; i++) U.set(list[i], value);
      },
      width(name, fraction) {
        const list = all(name);
        for (let i = 0; i < list.length; i++) U.width(list[i], fraction);
      },
      css(name, prop, value) {
        const list = all(name);
        for (let i = 0; i < list.length; i++) U.css(list[i], prop, value);
      },
    };
  }

  // ------------------------------------------------------------------------
  // Ferramentas de desenho
  // ------------------------------------------------------------------------

  function svg(name, attrs, text) {
    const node = document.createElementNS(NS, name);
    if (attrs) for (const k in attrs) node.setAttribute(k, attrs[k]);
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function arcPath(cx, cy, r, a0, a1) {
    const rad = (a) => (a * Math.PI) / 180;
    const x0 = cx + r * Math.cos(rad(a0));
    const y0 = cy + r * Math.sin(rad(a0));
    const x1 = cx + r * Math.cos(rad(a1));
    const y1 = cy + r * Math.sin(rad(a1));
    const large = a1 - a0 > 180 ? 1 : 0;
    return `M${x0.toFixed(1)} ${y0.toFixed(1)} A${r} ${r} 0 ${large} 1 ${x1.toFixed(1)} ${y1.toFixed(1)}`;
  }

  /**
   * Desenha marcações e números no mostrador.
   * Ângulos no padrão canvas (0° = leste, sentido horário), início em 150°.
   */
  function buildDial(face, cfg) {
    const {
      min = 0,
      max = 180,
      major = 20,
      minor = 10,
      cx = 210,
      cy = 210,
      r = 190,
      format = (v) => String(v),
      redzone = null,
    } = cfg;

    const start = 150;
    const sweep = 240;
    const ticks = face.querySelector('[data-ticks]');
    const labels = face.querySelector('[data-labels]');
    const red = face.querySelector('[data-redzone]');

    // aro e disco de fundo, para a escala não ficar "flutuando"
    face.insertBefore(svg('circle', { cx, cy, r: r + 4, fill: 'none', stroke: '#1B2532', 'stroke-width': '2' }), face.firstChild);
    face.insertBefore(svg('circle', { cx, cy, r: r - 46, fill: 'rgba(232,238,244,0.018)' }), face.firstChild);

    if (red && redzone !== null) {
      const f0 = (redzone - min) / (max - min);
      red.setAttribute('d', arcPath(cx, cy, r - 9, start + sweep * f0 + 1.5, start + sweep - 1.5));
      red.setAttribute('stroke-width', '15');
      red.setAttribute('stroke-linecap', 'round');
      red.setAttribute('fill', 'none');
    }

    const total = Math.round((max - min) / minor);
    const majorEvery = Math.max(1, Math.round(major / minor));

    for (let i = 0; i <= total; i++) {
      const v = min + i * minor;
      const f = (v - min) / (max - min);
      const a = ((start + sweep * f) * Math.PI) / 180;
      const isMajor = i % majorEvery === 0;
      const r2 = isMajor ? r - 26 : r - 13;
      ticks.appendChild(
        svg('line', {
          x1: (cx + Math.cos(a) * r).toFixed(1),
          y1: (cy + Math.sin(a) * r).toFixed(1),
          x2: (cx + Math.cos(a) * r2).toFixed(1),
          y2: (cy + Math.sin(a) * r2).toFixed(1),
          stroke: isMajor ? '#7C8B9A' : '#35434F',
          'stroke-width': isMajor ? '2.6' : '1.4',
          'stroke-linecap': 'round',
        }),
      );
    }

    if (labels) {
      for (let v = min; v <= max + 1e-9; v += major) {
        const f = (v - min) / (max - min);
        const a = ((start + sweep * f) * Math.PI) / 180;
        const lx = cx + Math.cos(a) * (r - 62);
        const ly = cy + Math.sin(a) * (r - 62) + 11;
        labels.appendChild(
          svg(
            'text',
            {
              x: lx.toFixed(1),
              y: ly.toFixed(1),
              fill: '#8A99A8',
              'font-size': '27',
              'font-family': '"Bahnschrift", "Segoe UI", sans-serif',
              'text-anchor': 'middle',
            },
            format(v),
          ),
        );
      }
    }
  }

  function gearLabel(S) {
    return S.mode + S.gear;
  }

  function hms(seconds) {
    const s = Math.max(0, Math.floor(seconds));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    return [h, m, sec].map((n) => String(n).padStart(2, '0')).join(':');
  }

  /**
   * Converte uma série em `line` + `area` de SVG, ancorada à direita.
   * `slots` é a quantidade máxima de amostras (30 s a 10 Hz = 300).
   */
  function pathFrom(arr, max, W, H, pad, slots) {
    const n = arr.length;
    if (n < 2) return { line: '', area: '' };
    let d = '';
    for (let i = 0; i < n; i++) {
      const x = ((i + (slots - n)) / (slots - 1)) * W;
      const y = H - pad - clamp(arr[i] / max, 0, 1) * (H - pad * 2);
      d += (i === 0 ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1) + ' ';
    }
    const x0 = ((slots - n) / (slots - 1)) * W;
    return { line: d, area: d + `L${W} ${H} L${x0.toFixed(1)} ${H} Z` };
  }

  // ------------------------------------------------------------------------
  // Metadados usados pela galeria
  // ------------------------------------------------------------------------

  const LAYOUTS = [
    {
      id: 'needles',
      name: 'Agulhas',
      tag: 'analógico',
      desc: 'Dois relógios com agulha de verdade, escala gravada e mostrador com zona vermelha. Familiar para quem já dirigiu qualquer carro.',
    },
    {
      id: 'tape',
      name: 'Fitas',
      tag: 'rolagem',
      desc: 'Velocidade e rotação em fitas verticais que rolam continuamente. Leitura por posição além do número — bom para percepção de tendência.',
    },
    {
      id: 'shift',
      name: 'Cockpit esportivo',
      tag: 'desempenho',
      desc: 'Barra de LEDs de rotação, luz de troca de marcha e velocidade dominante. Mostra o motor trabalhando perto do limite.',
    },
    {
      id: 'eco',
      name: 'Eco',
      tag: 'eficiência',
      desc: 'Consumo instantâneo, pontuação de eficiência e dica de condução. Foco em dirigir econômico, não em desempenho.',
    },
    {
      id: 'thermal',
      name: 'Térmico',
      tag: 'temperaturas',
      desc: 'Termômetro vertical com zonas, ventoinha e temperaturas de apoio. Para acompanhar aquecimento e esforço térmico.',
    },
    {
      id: 'trend',
      name: 'Tendência',
      tag: 'gráficos',
      desc: 'Quatro faixas com os últimos 30 segundos de cada grandeza. Responde "está subindo?" sem precisar memorizar o número anterior.',
    },
    {
      id: 'radar',
      name: 'Radar',
      tag: 'visão geral',
      desc: 'Um hexágono com seis grandezas normalizadas. Mostra o "formato" da condução atual em uma única forma.',
    },
    {
      id: 'diag',
      name: 'Diagnóstico',
      tag: 'falhas',
      desc: 'Códigos de falha, estado da luz de injeção, monitores e saúde do enlace OBD2. É a tela de manutenção, não a de direção.',
    },
    {
      id: 'trip',
      name: 'Viagem',
      tag: 'acumulados',
      desc: 'Computador de bordo: distância, tempo, médias, autonomia e combustível usado, com botão de zerar.',
    },
    {
      id: 'day',
      name: 'Diurno',
      tag: 'tema claro',
      desc: 'A mesma leitura com fundo claro e tinta escura. Alternativa para sol forte, onde o tema escuro vira espelho.',
    },
    {
      id: 'hud',
      name: 'HUD',
      tag: 'noturno',
      desc: 'Monocromático, baixo brilho, só o essencial. Imita projeção no para-brisa para não ofuscar à noite.',
    },
    {
      id: 'bento',
      name: 'Mosaico',
      tag: 'cartões',
      desc: 'Cartões de tamanhos diferentes, anéis de progresso e hierarquia por área. Moderno, com o principal bem maior que o resto.',
    },
  ];

  // ==========================================================================
  // 01 · AGULHAS
  // ==========================================================================
  const needles = {
    mount(root) {
      const b = scope(root);
      const dials = b.qa('.dial');
      buildDial(dials[0].querySelector('svg'), { min: 0, max: 180, major: 20, minor: 10, format: (v) => String(v) });
      buildDial(dials[1].querySelector('svg'), { min: 0, max: 7, major: 1, minor: 0.5, format: (v) => String(v), redzone: 6 });

      const needleSpeed = b.q('[data-needle="speed"]');
      const needleRpm = b.q('[data-needle="rpm"]');
      const waterFill = b.q('[data-bind="waterFill"]');

      return {
        update(S) {
          const fs = clamp(S.speed / 180, 0, 1);
          const fr = clamp(S.rpm / 7000, 0, 1);
          needleSpeed.style.transform = `rotate(${240 + 240 * fs}deg)`;
          needleRpm.style.transform = `rotate(${240 + 240 * fr}deg)`;

          b.set('speed', U.int(S.speed));
          b.set('rpm', U.int(S.rpm));
          b.set('coolant', U.int(S.coolant));
          b.set('fuel', U.int(S.fuel));
          b.set('voltage', U.dec(S.voltage, 1));
          b.set('gear', gearLabel(S));
          b.set('map', U.int(S.map));
          b.set('maf', S.maf === null ? DASH : U.dec(S.maf, 1));
          b.set('intake', U.int(S.intake));
          b.set('fps', String(Math.round(S.fps)));

          const water = clamp((S.coolant - 40) / 80, 0, 1);
          const clip = 'inset(0 ' + ((1 - water) * 100).toFixed(1) + '% 0 0)';
          if (waterFill.style.clipPath !== clip) waterFill.style.clipPath = clip;

          b.width('fuelFill', S.fuel / 100);
          b.width('voltFill', (S.voltage - 11) / 4);
          b.css('voltFill', '--fill-color', S.voltage < 13.2 ? 'var(--warn)' : 'var(--ok)');
        },
      };
    },
  };

  // ==========================================================================
  // 02 · FITAS
  // ==========================================================================
  function buildTape(col, opts) {
    const { min, max, step, rowHeight, format } = opts;
    const count = Math.round((max - min) / step);
    const rows = [];
    for (let i = 0; i <= count; i++) {
      const d = document.createElement('div');
      d.className = 'row';
      d.textContent = format(min + i * step);
      col.appendChild(d);
      rows.push(d);
    }
    const windowEl = col.parentElement;
    const height = windowEl.clientHeight;
    let lastOn = -1;

    return function setTape(value) {
      const pos = clamp((value - min) / step, 0, count);
      const y = height / 2 - rowHeight / 2 - pos * rowHeight;
      col.style.transform = `translate3d(0, ${y.toFixed(2)}px, 0)`;
      const idx = clamp(Math.round(pos), 0, count);
      if (idx !== lastOn) {
        if (rows[lastOn]) rows[lastOn].classList.remove('on');
        rows[idx].classList.add('on');
        lastOn = idx;
      }
    };
  }

  const tape = {
    mount(root) {
      const b = scope(root);
      const setSpeedTape = buildTape(b.q('[data-tape="speed"]'), {
        min: 0,
        max: 180,
        step: 5,
        rowHeight: 68,
        format: (v) => String(v),
      });
      const setRpmTape = buildTape(b.q('[data-tape="rpm"]'), {
        min: 0,
        max: 7000,
        step: 250,
        rowHeight: 52,
        format: (v) => String(v),
      });

      return {
        update(S) {
          setSpeedTape(S.speed);
          setRpmTape(S.rpm);

          b.set('coolant', U.int(S.coolant));
          b.set('load', U.int(S.load));
          b.set('throttle', U.int(S.throttle));
          b.set('voltage', U.dec(S.voltage, 1));
          b.set('map', U.int(S.map));
          b.set('maf', S.maf === null ? DASH : U.dec(S.maf, 1));
          b.set('intake', U.int(S.intake));
          b.set('fuel', U.int(S.fuel));
          b.set('gear', String(S.gear));
          b.set('fps', String(Math.round(S.fps)));

          b.width('waterFill', (S.coolant - 40) / 80);
          b.width('loadFill', S.load / 100);
          b.width('throttleFill', S.throttle / 100);
          b.width('voltFill', (S.voltage - 11) / 4);
        },
      };
    },
  };

  // ==========================================================================
  // 03 · COCKPIT ESPORTIVO
  // ==========================================================================
  const shift = {
    mount(root) {
      const b = scope(root);
      const ledsEl = b.q('[data-leds]');
      const LEDS = 30;
      const leds = [];
      for (let i = 0; i < LEDS; i++) {
        const led = document.createElement('i');
        if (i >= 23) led.className = 'red';
        else if (i >= 16) led.className = 'amber';
        ledsEl.appendChild(led);
        leds.push(led);
      }

      return {
        update(S) {
          const f = clamp((S.rpm - 600) / (7000 - 600), 0, 1);
          const on = Math.round(f * LEDS);
          for (let i = 0; i < LEDS; i++) {
            const active = i < on;
            if (leds[i].classList.contains('on') !== active) leds[i].classList.toggle('on', active);
          }

          const rev = S.rpm > 6200;
          if (root.classList.contains('rev') !== rev) root.classList.toggle('rev', rev);

          b.set('rpm', U.int(S.rpm));
          b.set('speed', U.int(S.speed));
          b.set('gearLabel', gearLabel(S));
          b.set('modeLabel', S.mode === 'S' ? 'modo S' : 'modo D');
          b.set('coolant', U.int(S.coolant));
          b.set('voltage', U.dec(S.voltage, 1));
          b.set('fuel', U.int(S.fuel));
          b.set('throttle', U.int(S.throttle));
          b.set('load', U.int(S.load));
          b.set('map', U.int(S.map));
          b.set('maf', S.maf === null ? DASH : U.dec(S.maf, 1));
          b.set('timing', U.int(S.timing));
          b.set('intake', U.int(S.intake));
          b.set('fps', String(Math.round(S.fps)));

          b.width('throttleFill', S.throttle / 100);
          b.width('loadFill', S.load / 100);
        },
      };
    },
  };

  // ==========================================================================
  // 04 · ECO
  // ==========================================================================
  const eco = {
    mount(root) {
      const b = scope(root);
      const ring = b.q('[data-ring]');
      const R = 98;
      const C = 2 * Math.PI * R;
      ring.style.strokeDasharray = String(C);

      const ecoLine = b.q('[data-path="ecoLine"]');
      const ecoArea = b.q('[data-path="ecoArea"]');

      const leavesEl = b.q('[data-leaves]');
      const leaves = [];
      for (let i = 0; i < 5; i++) {
        const leaf = document.createElement('i');
        leavesEl.appendChild(leaf);
        leaves.push(leaf);
      }

      function tip(S) {
        if (S.maf === null) return 'Sem leitura de MAF — consumo indisponível';
        if (S.speed < 3) return 'Parado · motor em marcha lenta';
        if (S.l100 > 11) return 'Consumo alto · alivie o acelerador';
        if (S.l100 > 8) return 'Aceleração forte · suba a marcha mais cedo';
        if (S.l100 > 6) return 'Ritmo alto · mantenha a velocidade constante';
        if (S.l100 > 4.5) return 'Bom ritmo · fluxo constante';
        return 'Muito eficiente · mantenha assim';
      }

      return {
        update(S) {
          ring.style.strokeDashoffset = String(C * (1 - clamp(S.ecoScore / 100, 0, 1)));

          const on = Math.round(S.ecoScore / 20);
          for (let i = 0; i < leaves.length; i++) {
            const active = i < on;
            if (leaves[i].classList.contains('on') !== active) leaves[i].classList.toggle('on', active);
          }

          b.set('ecoScore', U.int(S.ecoScore));

          if (S.l100 !== null) {
            b.set('cons', U.dec(S.l100, 1));
            b.set('consUnit', 'L/100 km');
          } else if (S.lph !== null) {
            b.set('cons', U.dec(S.lph, 1));
            b.set('consUnit', 'L/h');
          } else {
            b.set('cons', DASH);
            b.set('consUnit', 'L/100 km');
          }

          b.set('speed', U.int(S.speed));
          b.set('range', U.int(S.range));
          b.set('avgCons', S.avgCons === null ? DASH : U.dec(S.avgCons, 1));
          b.set('fuel', U.int(S.fuel));
          b.set('tip', tip(S));
          b.set('maf', S.maf === null ? DASH : U.dec(S.maf, 1));
          b.set('intake', U.int(S.intake));
          b.set('voltage', U.dec(S.voltage, 1));
          b.set('gearLabel', gearLabel(S));
          b.set('tripKm', U.dec(S.tripKm, 1));
          b.set('fps', String(Math.round(S.fps)));

          const history = S.history.eco;
          const series = pathFrom(history, 100, 900, 90, 8, global.SIM.HISTORY_MAX);
          ecoLine.setAttribute('d', series.line);
          ecoArea.setAttribute('d', series.area);
          let min = Infinity;
          for (const v of history) if (v < min) min = v;
          b.set('ecoMin', history.length ? U.int(min) : DASH);
        },
      };
    },
  };

  // ==========================================================================
  // 05 · TÉRMICO
  // ==========================================================================
  const thermal = {
    mount(root) {
      const b = scope(root);
      const stateEl = b.q('[data-state]');
      const fanEl = b.q('[data-fan]');
      const waterFill = b.q('[data-bind="waterFill"]');

      function state(S) {
        if (S.coolant < 75) return ['Aquecendo', 'cold'];
        if (S.coolant < 97) return ['Normal', 'ok'];
        if (S.coolant < 104) return ['Alto', 'warn'];
        return ['Crítico', 'danger'];
      }

      return {
        update(S) {
          const [label, kind] = state(S);
          if (stateEl.textContent !== label) stateEl.textContent = label;
          if (stateEl.dataset.kind !== kind) stateEl.dataset.kind = kind;

          b.set('coolant', U.int(S.coolant));
          b.set('intake', U.int(S.intake));
          b.set('timing', U.int(S.timing));
          b.set('map', U.int(S.map));
          b.set('load', U.int(S.load));
          b.set('voltage', U.dec(S.voltage, 1));
          b.set('maf', S.maf === null ? DASH : U.dec(S.maf, 1));
          b.set('fuel', U.int(S.fuel));
          b.set('gear', String(S.gear));
          b.set('fps', String(Math.round(S.fps)));
          b.set('fanLabel', S.fan ? 'ligada' : 'parada');

          const fraction = clamp((S.coolant - 40) / 80, 0, 1);
          const clip = 'inset(' + ((1 - fraction) * 100).toFixed(1) + '% 0 0 0)';
          if (waterFill.style.clipPath !== clip) waterFill.style.clipPath = clip;
          if (fanEl.classList.contains('on') !== S.fan) fanEl.classList.toggle('on', S.fan);
        },
      };
    },
  };

  // ==========================================================================
  // 06 · TENDÊNCIA
  // ==========================================================================
  const trend = {
    mount(root) {
      const b = scope(root);
      const W = 900;
      const H = 120;
      const PAD = 10;
      const MAX = global.SIM ? global.SIM.HISTORY_MAX : 300;

      const series = [
        { key: 'speed', max: 180, fill: 'true' },
        { key: 'rpm', max: 7000 },
        { key: 'load', max: 100 },
        { key: 'throttle', max: 100 },
      ].map((cfg) => ({
        ...cfg,
        line: b.q(`[data-path="${cfg.key}Line"]`),
        area: b.q(`[data-path="${cfg.key}Area"]`),
        maxBind: cfg.key + 'Max',
      }));

      return {
        update(S) {
          for (const s of series) {
            const arr = S.history[s.key];
            const n = arr.length;
            if (n < 2) continue;

            let d = '';
            let min = Infinity;
            let max = -Infinity;
            for (let i = 0; i < n; i++) {
              const v = arr[i];
              if (v < min) min = v;
              if (v > max) max = v;
              const x = ((i + (MAX - n)) / (MAX - 1)) * W;
              const y = H - PAD - clamp(v / s.max, 0, 1) * (H - PAD * 2);
              d += (i === 0 ? 'M' : 'L') + x.toFixed(1) + ' ' + y.toFixed(1) + ' ';
            }
            const x0 = ((MAX - n) / (MAX - 1)) * W;
            const area = d + `L${W} ${H} L${x0.toFixed(1)} ${H} Z`;

            s.line.setAttribute('d', d);
            s.area.setAttribute('d', area);
            b.set(s.maxBind, U.int(max));
          }

          b.set('speed', U.int(S.speed));
          b.set('rpm', U.int(S.rpm));
          b.set('load', U.int(S.load));
          b.set('throttle', U.int(S.throttle));
          b.set('fps', String(Math.round(S.fps)));
        },
      };
    },
  };

  // ==========================================================================
  // 07 · RADAR
  // ==========================================================================
  const radar = {
    mount(root) {
      const b = scope(root);
      const svgEl = b.q('svg');
      const ringsG = b.q('[data-rings]');
      const axesG = b.q('[data-axes]');
      const labelsG = b.q('[data-axis-labels]');
      const dotsG = b.q('[data-dots]');
      const poly = b.q('[data-poly]');

      const CX = 280;
      const CY = 300;
      const R = 250;
      const axes = [
        { key: 'speed', label: 'Velocidade', unit: 'km/h', max: 180, get: (S) => S.speed },
        { key: 'rpm', label: 'Rotação', unit: 'rpm', max: 7000, get: (S) => S.rpm },
        { key: 'load', label: 'Carga', unit: '%', max: 100, get: (S) => S.load },
        { key: 'throttle', label: 'Acelerador', unit: '%', max: 100, get: (S) => S.throttle },
        { key: 'coolant', label: 'Temperatura', unit: '°C', min: 40, max: 120, get: (S) => S.coolant },
        { key: 'voltage', label: 'Bateria', unit: 'V', min: 11, max: 15, get: (S) => S.voltage },
      ];

      const angleOf = (i) => (-90 + i * (360 / axes.length)) * (Math.PI / 180);

      function pointAt(i, radius) {
        const a = angleOf(i);
        return [CX + Math.cos(a) * radius, CY + Math.sin(a) * radius];
      }

      // anéis
      for (const ratio of [0.25, 0.5, 0.75, 1]) {
        const pts = axes.map((_, i) => pointAt(i, R * ratio).map((v) => v.toFixed(1)).join(','));
        ringsG.appendChild(
          svg('polygon', {
            points: pts.join(' '),
            fill: 'none',
            stroke: ratio === 1 ? '#2A3846' : '#1E2A36',
            'stroke-width': ratio === 1 ? '2' : '1',
          }),
        );
      }

      // eixos
      axes.forEach((_, i) => {
        const [x, y] = pointAt(i, R);
        axesG.appendChild(
          svg('line', { x1: CX, y1: CY, x2: x.toFixed(1), y2: y.toFixed(1), stroke: '#1E2A36', 'stroke-width': '1.5' }),
        );
      });

      // rótulos
      axes.forEach((axis, i) => {
        const a = angleOf(i);
        const lx = CX + Math.cos(a) * (R + 24);
        const ly = CY + Math.sin(a) * (R + 24);
        const anchor = Math.abs(Math.cos(a)) < 0.3 ? 'middle' : Math.cos(a) > 0 ? 'start' : 'end';
        const t = svg(
          'text',
          {
            x: lx.toFixed(1),
            y: (ly + (Math.sin(a) > 0.6 ? 20 : Math.sin(a) < -0.6 ? -10 : 6)).toFixed(1),
            fill: '#8A99A8',
            'font-size': '17',
            'font-family': '"Inter", "Segoe UI", sans-serif',
            'letter-spacing': '2',
            'text-anchor': anchor,
          },
          axis.label.toUpperCase(),
        );
        labelsG.appendChild(t);
      });

      // pontos
      const dots = axes.map(() => {
        const c = svg('circle', { cx: CX, cy: CY, r: '6', fill: '#35C2FF', stroke: '#0B0F14', 'stroke-width': '2' });
        dotsG.appendChild(c);
        return c;
      });

      // legenda
      const legend = b.q('[data-legend]');
      const rows = axes.map((axis) => {
        const row = document.createElement('div');
        row.className = 'rd-row';
        const name = document.createElement('span');
        name.textContent = axis.label;
        const value = document.createElement('b');
        value.textContent = DASH;
        const bar = document.createElement('i');
        const fill = document.createElement('em');
        bar.appendChild(fill);
        row.append(name, value, bar);
        legend.appendChild(row);
        return { value, fill, axis };
      });

      return {
        update(S) {
          const pts = axes.map((axis, i) => {
            const v = axis.get(S);
            const min = axis.min || 0;
            const f = clamp((v - min) / (axis.max - min), 0, 1);
            const [x, y] = pointAt(i, Math.max(10, R * f));
            dots[i].setAttribute('cx', x.toFixed(1));
            dots[i].setAttribute('cy', y.toFixed(1));
            return [x, y];
          });
          poly.setAttribute('points', pts.map((p) => p[0].toFixed(1) + ',' + p[1].toFixed(1)).join(' '));

          b.set('speed', U.int(S.speed));
          b.set('fps', String(Math.round(S.fps)));

          for (const r of rows) {
            const v = r.axis.get(S);
            const min = r.axis.min || 0;
            const f = clamp((v - min) / (r.axis.max - min), 0, 1);
            const digits = r.axis.key === 'voltage' ? 1 : 0;
            U.set(r.value, digits ? U.dec(v, 1) : U.int(v));
            U.width(r.fill, f);
            if (r.axis.key === 'voltage' && v < 13.2) r.value.style.color = 'var(--warn)';
            else r.value.style.color = '';
          }
        },
      };
    },
  };

  // ==========================================================================
  // 08 · DIAGNÓSTICO
  // ==========================================================================
  const diag = {
    mount(root) {
      const b = scope(root);
      const statusEl = b.q('[data-status]');
      const glyph = b.q('[data-glyph]');
      const mil = b.q('[data-mil]');
      const rowsEl = b.q('[data-rows]');
      const emptyEl = b.q('[data-empty]');

      const PIDS = [
        { name: 'Velocidade', pid: '0x0D', key: 'speed' },
        { name: 'Rotação', pid: '0x0C', key: 'rpm' },
        { name: 'Água', pid: '0x05', key: 'coolant' },
        { name: 'Carga', pid: '0x04', key: 'load' },
        { name: 'Acelerador', pid: '0x11', key: 'throttle' },
        { name: 'Ar admitido', pid: '0x0F', key: 'intake' },
        { name: 'MAF', pid: '0x10', key: 'maf' },
        { name: 'Bateria', pid: '0x42', key: 'voltage' },
      ];

      const healthEl = b.q('[data-health]');
      const health = PIDS.map((p, i) => {
        const row = document.createElement('div');
        row.className = 'dg-hrow';
        const dot = document.createElement('i');
        const name = document.createElement('span');
        name.textContent = p.name;
        const pid = document.createElement('em');
        pid.textContent = p.pid;
        const lat = document.createElement('b');
        lat.textContent = '—';
        row.append(dot, name, pid, lat);
        healthEl.appendChild(row);
        return { row, dot, lat, p, i };
      });

      const monitorsEl = b.q('[data-monitors]');
      const MONITORS = ['Ignição', 'Catalisador', 'Sonda O₂', 'EVAP'];
      const monitors = MONITORS.map((label) => {
        const chip = document.createElement('span');
        chip.className = 'dg-chip';
        const name = document.createElement('span');
        name.textContent = label;
        const val = document.createElement('b');
        val.textContent = 'OK';
        chip.append(name, val);
        monitorsEl.appendChild(chip);
        return { chip, val };
      });

      let signature = '';

      return {
        update(S) {
          const count = S.dtcs.length;
          const hasFault = count > 0;

          const title = hasFault ? (count === 1 ? '1 falha ativa' : count + ' falhas ativas') : 'Sistema OK';
          const sub = hasFault
            ? 'Luz de injeção acesa · leitura concluída'
            : 'Nenhum código confirmado · monitores prontos';
          b.set('statusTitle', title);
          b.set('statusSub', sub);
          b.set('dtcCount', String(count));

          const kind = hasFault ? 'fault' : 'ok';
          if (statusEl.dataset.kind !== kind) statusEl.dataset.kind = kind;
          U.set(glyph, hasFault ? '!' : '✓');
          if (mil.classList.contains('on') !== S.mil) mil.classList.toggle('on', S.mil);

          // lista de códigos
          const sig = S.dtcs.map((d) => d.code).join('|');
          if (sig !== signature) {
            signature = sig;
            rowsEl.innerHTML = '';
            emptyEl.hidden = hasFault;
            for (const d of S.dtcs) {
              const row = document.createElement('div');
              row.className = 'dg-row';
              row.innerHTML =
                '<b>' + d.code + '</b>' +
                '<span>' + d.desc + '</span>' +
                '<em class="sev ' + d.severity + '">' + (d.severity === 'grave' ? 'Grave' : 'Moderada') + '</em>';
              rowsEl.appendChild(row);
              requestAnimationFrame(() => row.classList.add('in'));
            }
          }

          // saúde do enlace
          for (const h of health) {
            let missing = false;
            if (h.p.key === 'maf') missing = S.maf === null;
            const latency = Math.round(38 + 13 * Math.abs(Math.sin(S.t * 0.9 + h.i)));
            U.set(h.lat, missing ? 'sem' : latency + ' ms');
            const status = missing ? 'down' : 'ok';
            if (h.row.dataset.status !== status) h.row.dataset.status = status;
          }

          // monitores
          for (const m of monitors) {
            const pending = hasFault && m === monitors[3];
            U.set(m.val, pending ? 'pendente' : 'OK');
            const status = pending ? 'pending' : 'ok';
            if (m.chip.dataset.status !== status) m.chip.dataset.status = status;
          }
          b.set('monitorNote', hasFault ? '3 de 4 prontos' : '4 de 4 prontos');

          b.set('hz', String(Math.round(S.fps)));
          b.set('lastMs', String(Math.round(38 + 13 * Math.abs(Math.sin(S.t * 0.9)))));
          b.set('fps', String(Math.round(S.fps)));
        },
      };
    },
  };

  // ==========================================================================
  // 09 · VIAGEM
  // ==========================================================================
  const trip = {
    mount(root) {
      const b = scope(root);
      const btn = b.q('[data-action="resetTrip"]');
      let flash = 0;

      if (btn) {
        btn.addEventListener('click', (event) => {
          event.stopPropagation();
          global.SIM.resetTrip();
          flash = 1;
        });
      }

      return {
        update(S) {
          b.set('tripKm', U.dec(S.tripKm, 1));
          b.set('tripTime', hms(S.tripSec));
          b.set('avgSpeed', U.int(S.avgSpeed));
          b.set('avgCons', S.avgCons === null ? DASH : U.dec(S.avgCons, 1));
          b.set('range', U.int(S.range));
          b.set('fuelUsed', U.dec(S.fuelUsed, 2));
          b.set('fuel', U.int(S.fuel));
          b.set('cons', S.l100 === null ? (S.lph === null ? DASH : U.dec(S.lph, 1)) : U.dec(S.l100, 1));
          b.set('consUnit', S.l100 === null && S.lph !== null ? 'L/h' : 'L/100');
          b.set('fps', String(Math.round(S.fps)));
          b.width('fuelFill', S.fuel / 100);

          if (flash > 0) {
            flash = Math.max(0, flash - 0.02);
            const active = flash > 0.5;
            if (root.classList.contains('reset') !== active) root.classList.toggle('reset', active);
          }
        },
      };
    },
  };

  // ==========================================================================
  // 10 · DIURNO
  // ==========================================================================
  const day = {
    mount(root) {
      const b = scope(root);
      return {
        update(S) {
          b.set('speed', U.int(S.speed));
          b.set('rpm', U.int(S.rpm));
          b.set('coolant', U.int(S.coolant));
          b.set('intake', U.int(S.intake));
          b.set('load', U.int(S.load));
          b.set('throttle', U.int(S.throttle));
          b.set('voltage', U.dec(S.voltage, 1));
          b.set('fuel', U.int(S.fuel));
          b.set('map', U.int(S.map));
          b.set('maf', S.maf === null ? DASH : U.dec(S.maf, 1));
          b.set('gear', String(S.gear));
          b.set('fps', String(Math.round(S.fps)));
          b.width('rpmFill', S.rpm / 7000);
        },
      };
    },
  };

  // ==========================================================================
  // 11 · HUD
  // ==========================================================================
  const hud = {
    mount(root) {
      const b = scope(root);
      return {
        update(S) {
          b.set('speed', U.int(S.speed));
          b.set('gearLabel', gearLabel(S));
          b.set('coolant', U.int(S.coolant));
          b.set('fuel', U.int(S.fuel));
          b.set('voltage', U.dec(S.voltage, 1));
          b.set('fps', String(Math.round(S.fps)));
          b.width('rpmFill', S.rpm / 7000);

          const rev = S.rpm > 6300;
          if (root.classList.contains('rev') !== rev) root.classList.toggle('rev', rev);

          const warn = S.coolant > 102;
          if (root.classList.contains('warn') !== warn) root.classList.toggle('warn', warn);
        },
      };
    },
  };

  // ==========================================================================
  // 12 · MOSAICO
  // ==========================================================================
  const bento = {
    mount(root) {
      const b = scope(root);
      const R = 62;
      const C = 2 * Math.PI * R;
      const ringRpm = root.querySelector('[data-ringcard="rpm"] [data-ring]');
      const ringCool = root.querySelector('[data-ringcard="coolant"] [data-ring]');
      for (const ring of [ringRpm, ringCool]) {
        ring.style.strokeDasharray = String(C);
        ring.style.strokeDashoffset = String(C);
      }

      return {
        update(S) {
          ringRpm.style.strokeDashoffset = String(C * (1 - clamp(S.rpm / 7000, 0, 1)));
          ringCool.style.strokeDashoffset = String(C * (1 - clamp((S.coolant - 40) / 80, 0, 1)));

          const coolKind = S.coolant > 104 ? 'danger' : S.coolant > 97 ? 'warn' : 'ok';
          if (root.dataset.cool !== coolKind) root.dataset.cool = coolKind;

          b.set('speed', U.int(S.speed));
          b.set('rpm', U.int(S.rpm));
          b.set('coolant', U.int(S.coolant));
          b.set('load', U.int(S.load));
          b.set('throttle', U.int(S.throttle));
          b.set('voltage', U.dec(S.voltage, 1));
          b.set('fuel', U.int(S.fuel));
          b.set('map', U.int(S.map));
          b.set('maf', S.maf === null ? DASH : U.dec(S.maf, 1));
          b.set('intake', U.int(S.intake));
          b.set('timing', U.int(S.timing));
          b.set('gearLabel', gearLabel(S));
          b.set('tripKm', U.dec(S.tripKm, 1));
          b.set('fps', String(Math.round(S.fps)));

          b.width('loadFill', S.load / 100);
          b.width('throttleFill', S.throttle / 100);
          b.width('voltFill', (S.voltage - 11) / 4);
          b.width('fuelFill', S.fuel / 100);
        },
      };
    },
  };

  // --------------------------------------------------------------------------
  // Registro
  // --------------------------------------------------------------------------
  global.U = U;
  global.LAYOUTS = LAYOUTS;
  global.Screens = { needles, tape, shift, eco, thermal, trend, radar, diag, trip, day, hud, bento };
})(window);
