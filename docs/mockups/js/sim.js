/* ==========================================================================
   Simulador de telemetria dos mockups — Yaris OBD
   --------------------------------------------------------------------------
   Não tem nada a ver com o simulador real do app (VehicleSimulator.kt). Aqui
   é só um modelo de comportamento para dar vida aos protótipos de layout:
   perfis de velocidade por cenário, marchas, aquecimento do motor, consumo
   derivado do MAF, falhas injetadas e histórico para os gráficos.

   Todas as grandezas seguem o mesmo contrato do app: `null` = indisponível.
   ========================================================================== */
(function (global) {
  'use strict';

  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const lerp = (a, b, t) => a + (b - a) * t;

  // Faixas usadas pelos relógios (iguais às de GaugeMath.kt).
  const RPM_MAX = 7000;
  const SPEED_MAX = 180;
  const COOLANT_MIN = 40;
  const COOLANT_MAX = 120;

  const RATIOS = [0, 74, 52, 38, 30, 25, 21]; // rpm por km/h em cada marcha
  const GEAR_UP = [0, 25, 45, 70, 95, 120];   // velocidade de troca

  // ------------------------------------------------------------------------
  // Perfis de velocidade (km/h). Tempo em segundos; o perfil roda em loop.
  // ------------------------------------------------------------------------
  const PROFILES = {
    normal: [
      { t: 0, v: 0 }, { t: 7, v: 0 }, { t: 16, v: 46 }, { t: 26, v: 58 },
      { t: 44, v: 60 }, { t: 52, v: 24 }, { t: 60, v: 0 }, { t: 68, v: 0 },
      { t: 78, v: 38 }, { t: 94, v: 44 }, { t: 102, v: 0 }, { t: 110, v: 0 },
    ],
    city: [
      { t: 0, v: 0 }, { t: 5, v: 0 }, { t: 13, v: 34 }, { t: 24, v: 38 },
      { t: 30, v: 0 }, { t: 36, v: 0 }, { t: 44, v: 26 }, { t: 54, v: 22 },
      { t: 60, v: 0 }, { t: 68, v: 0 }, { t: 76, v: 42 }, { t: 86, v: 0 },
      { t: 92, v: 0 },
    ],
    highway: [
      { t: 0, v: 86 }, { t: 30, v: 92 }, { t: 60, v: 104 },
      { t: 90, v: 96 }, { t: 120, v: 108 }, { t: 150, v: 94 },
    ],
    sport: [
      { t: 0, v: 0 }, { t: 4, v: 0 }, { t: 12, v: 92 }, { t: 18, v: 118 },
      { t: 24, v: 64 }, { t: 30, v: 30 }, { t: 36, v: 30 }, { t: 44, v: 110 },
      { t: 50, v: 132 }, { t: 58, v: 70 }, { t: 64, v: 0 }, { t: 72, v: 0 },
    ],
    cold: [
      { t: 0, v: 0 }, { t: 6, v: 0 }, { t: 15, v: 30 }, { t: 26, v: 44 },
      { t: 38, v: 52 }, { t: 52, v: 58 }, { t: 66, v: 60 }, { t: 78, v: 0 },
      { t: 86, v: 0 },
    ],
    fault: [
      { t: 0, v: 0 }, { t: 8, v: 0 }, { t: 18, v: 48 }, { t: 30, v: 58 },
      { t: 44, v: 52 }, { t: 54, v: 64 }, { t: 66, v: 0 }, { t: 74, v: 0 },
    ],
  };

  const SCENARIOS = {
    normal: { label: 'Normal (misto)', profile: 'normal' },
    city: { label: 'Cidade (para e anda)', profile: 'city' },
    highway: { label: 'Rodovia (90–110)', profile: 'highway' },
    sport: { label: 'Esportivo', profile: 'sport' },
    cold: { label: 'Motor frio (aquecendo)', profile: 'cold', cold: true, warmTau: 15 },
    hot: { label: 'Motor quente (ventoinha)', profile: 'sport', hot: true },
    fault: { label: 'Falha (P0301)', profile: 'fault', dtc: true },
    sensor: { label: 'PID ausente (MAF)', profile: 'normal', sensor: true },
  };

  const HISTORY_MAX = 300;   // 30 s a 10 Hz
  const SAMPLE_EVERY = 0.1;  // s

  const S = {
    scenario: 'normal',
    st: 0,          // tempo dentro do cenário
    t: 0,           // tempo total
    dt: 0,

    speed: 0,
    speedTarget: 0,
    rpm: 780,
    coolant: 88.5,
    intake: 31,
    load: 19,
    throttle: 5,
    map: 41,
    maf: 2.6,
    fuel: 64.2,
    fuelL: 26.96,     // litros restantes (tanque de 42 L)
    baro: 101.3,
    voltage: 14.12,
    ethanol: 27.4,
    timing: 11,
    gear: 1,
    mode: 'D',

    odometer: 48213.4,
    tripKm: 0,
    tripSec: 0,
    fuelUsed: 0,
    avgSpeed: 0,

    lph: 0.9,
    l100: null,
    ecoScore: 60,
    avgCons: null,
    range: 340,

    mil: false,
    dtcs: [],
    fan: false,

    fps: 14,
    shiftPulse: 0,
    harsh: 0,

    history: { speed: [], rpm: [], load: [], throttle: [], eco: [] },
    sampleT: 0,
  };

  // ------------------------------------------------------------------------
  // Utilidades de simulação
  // ------------------------------------------------------------------------

  function profileAt(profile, time) {
    const last = profile[profile.length - 1].t;
    const t = ((time % last) + last) % last;
    for (let i = 1; i < profile.length; i++) {
      if (t <= profile[i].t) {
        const a = profile[i - 1];
        const b = profile[i];
        const k = (t - a.t) / Math.max(0.001, b.t - a.t);
        return lerp(a.v, b.v, k);
      }
    }
    return profile[profile.length - 1].v;
  }

  function gearFor(speed) {
    let g = 1;
    for (let i = 1; i < GEAR_UP.length; i++) {
      if (speed >= GEAR_UP[i]) g = i + 1;
    }
    return clamp(g, 1, 6);
  }

  function addDtc(code, desc, severity) {
    if (S.dtcs.some((d) => d.code === code)) return;
    S.dtcs.push({
      code,
      desc,
      severity,
      at: S.t,
      confirmed: true,
    });
    S.mil = true;
  }

  function clearDtc() {
    S.dtcs = [];
    S.mil = false;
  }

  function setScenario(id) {
    const sc = SCENARIOS[id];
    if (!sc) return;
    S.scenario = id;
    S.st = 0;
    S.shiftPulse = 0;
    S.history = { speed: [], rpm: [], load: [], throttle: [], eco: [] };
    S.sampleT = 0;

    if (sc.cold) {
      S.coolant = 22.5;
      S.intake = 23;
      S.fan = false;
    }

    if (sc.hot) {
      S.coolant = 100.5;
      S.intake = 44;
      S.fan = true;
    }

    if (sc.dtc) {
      clearDtc();
    } else {
      clearDtc();
    }

    if (sc.sensor) {
      S.maf = null;
    } else if (S.maf === null) {
      S.maf = 2.6;
    }

    S.mode = id === 'sport' ? 'S' : 'D';
    S.tripKm = 0;
    S.tripSec = 0;
    S.fuelUsed = 0;
    S.avgSpeed = 0;
    S.avgCons = null;
  }

  function resetTrip() {
    S.tripKm = 0;
    S.tripSec = 0;
    S.fuelUsed = 0;
    S.avgSpeed = 0;
    S.avgCons = null;
    S.range = 340;
  }

  // ------------------------------------------------------------------------
  // Passo de simulação
  // ------------------------------------------------------------------------

  function update(rawDt) {
    const dt = clamp(rawDt, 0, 0.1);
    const sc = SCENARIOS[S.scenario];
    const prevSpeed = S.speed;

    S.dt = dt;
    S.t += dt;
    S.st += dt;

    // --- velocidade -------------------------------------------------------
    S.speedTarget = profileAt(PROFILES[sc.profile], S.st);
    const accel = S.scenario === 'sport' ? 24 : 12;   // km/h por segundo
    const decel = S.scenario === 'sport' ? 22 : 20;
    const delta = S.speedTarget - S.speed;
    S.speed = Math.max(0, S.speed + clamp(delta, -decel * dt, accel * dt));

    // --- acelerador -------------------------------------------------------
    const demand = S.speedTarget - S.speed;
    const thrTarget = clamp(
      4 + Math.max(0, demand) * 2.8 + S.speed * 0.055 + (S.speed < 1 ? 1.5 : 0),
      2,
      96,
    );
    S.throttle += (thrTarget - S.throttle) * Math.min(1, dt * 6);

    // --- marcha e rotação --------------------------------------------------
    const gear = gearFor(S.speed);
    if (gear !== S.gear) {
      S.gear = gear;
      S.shiftPulse = 1;
    }

    let rpmTarget;
    if (S.speed < 1.5) {
      rpmTarget = 780 + S.throttle * 14;
    } else {
      rpmTarget = 780 + S.speed * RATIOS[S.gear] * (0.78 + 0.5 * (S.throttle / 100));
    }
    if (S.scenario === 'sport' && S.throttle > 40) {
      rpmTarget *= 1.12;
    }
    S.rpm += (clamp(rpmTarget, 720, 7100) - S.rpm) * Math.min(1, dt * 7);

    // --- carga, pressão e fluxo de ar -------------------------------------
    const loadTarget = clamp(12 + S.throttle * 0.7 + (S.rpm / RPM_MAX) * 26, 5, 96);
    S.load += (loadTarget - S.load) * Math.min(1, dt * 4);

    if (S.maf !== null) {
      const mafTarget = 1.2 + S.rpm * S.load * 0.00012;
      S.maf += (mafTarget - S.maf) * Math.min(1, dt * 4);
    }

    S.map = 28 + 70 * (S.load / 100);

    // --- temperaturas ------------------------------------------------------
    const coolTarget = 87 + clamp((S.load - 35) * 0.18, 0, 16);
    const tau = S.coolant < coolTarget ? (sc.warmTau || 40) : 220;
    S.coolant += (coolTarget - S.coolant) * (1 - Math.exp(-dt / tau));
    S.fan = S.coolant > 95.5 ? true : S.coolant < 93.5 ? false : S.fan;

    const intakeTarget = 30 + S.load * 0.07 + 2.2 * Math.sin(S.t * 0.4) - (sc.cold ? 7 : 0);
    S.intake += (intakeTarget - S.intake) * Math.min(1, dt * 1.5);

    // --- elétrica e ignição ------------------------------------------------
    const voltTarget = 14.1 + 0.05 * Math.sin(S.t * 1.7) - (S.speed < 2 ? 0.16 : 0) - S.load * 0.002;
    S.voltage += (voltTarget - S.voltage) * Math.min(1, dt * 2);

    const timingTarget = 9 + (100 - S.load) * 0.26 + 1.4 * Math.sin(S.t * 0.9);
    S.timing += (timingTarget - S.timing) * Math.min(1, dt * 2);

    // --- consumo derivado do MAF (gasolina, 745 g/L, AFR 14,7) ------------
    S.lph = S.maf === null ? null : S.maf * 0.3287;
    if (S.lph !== null) {
      S.l100 = S.speed > 4 ? (S.lph / S.speed) * 100 : null;
      S.fuelUsed += (S.lph / 3600) * dt;
      S.fuelL = Math.max(0, S.fuelL - (S.lph / 3600) * dt);
      S.fuel = (S.fuelL / 42) * 100;
    } else {
      S.l100 = null;
    }

    // --- viagem ------------------------------------------------------------
    S.tripKm += (S.speed / 3600) * dt;
    S.tripSec += dt;
    S.avgSpeed = S.tripSec > 1 ? S.tripKm / (S.tripSec / 3600) : 0;
    S.avgCons = S.fuelUsed > 0.004 && S.tripKm > 0.05 ? (S.fuelUsed / S.tripKm) * 100 : null;
    S.range = S.fuelL / (S.avgCons !== null ? clamp(S.avgCons, 4, 20) : 7.6) * 100;

    // --- eficiência (só faz sentido com leitura de combustível) -----------
    if (S.l100 !== null) {
      S.ecoScore += (clamp(104 - S.l100 * 7.2, 0, 100) - S.ecoScore) * Math.min(1, dt * 1.2);
    } else if (S.lph !== null) {
      S.ecoScore += (clamp(104 - S.lph * 26, 0, 100) - S.ecoScore) * Math.min(1, dt * 1.2);
    } else {
      S.ecoScore += (40 - S.ecoScore) * Math.min(1, dt * 1.2);
    }

    // --- aceleração brusca (usada na dica do layout Eco) -------------------
    const dv = Math.abs(S.speed - prevSpeed) / Math.max(0.001, dt);
    S.harsh += ((dv > 6 ? 1 : 0) - S.harsh) * Math.min(1, dt * 3);

    // --- falhas ------------------------------------------------------------
    if (sc.dtc) {
      if (S.st > 1.8) addDtc('P0301', 'Falha de ignição detectada — cilindro 1', 'grave');
      if (S.st > 26) addDtc('P0171', 'Mistura pobre — banco 1 (sonda lambda)', 'moderada');
    }

    // --- histórico para os gráficos ---------------------------------------
    S.sampleT += dt;
    if (S.sampleT >= SAMPLE_EVERY) {
      S.sampleT -= SAMPLE_EVERY;
      push('speed', S.speed);
      push('rpm', S.rpm);
      push('load', S.load);
      push('throttle', S.throttle);
      push('eco', S.ecoScore);
    }

    // --- diversos ----------------------------------------------------------
    S.shiftPulse = Math.max(0, S.shiftPulse - dt * 3);
    S.fps = 12 + 2.4 * Math.abs(Math.sin(S.t * 0.7));
  }

  function push(key, value) {
    const arr = S.history[key];
    arr.push(value);
    if (arr.length > HISTORY_MAX) arr.shift();
  }

  // Estado inicial: carro já rodando, como na central ligada.
  setScenario('normal');
  S.coolant = 88.5;
  S.intake = 31;

  global.SIM = {
    state: S,
    SCENARIOS,
    setScenario,
    resetTrip,
    update,
    RPM_MAX,
    SPEED_MAX,
    COOLANT_MIN,
    COOLANT_MAX,
    HISTORY_MAX,
  };
})(window);
