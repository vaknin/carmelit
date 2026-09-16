// Hebrew calendar (Reingold & Dershowitz, "Calendrical Calculations") - just enough
// to place the Jewish holidays on which the Carmelit changes its timetable.
const HEBREW_EPOCH = -1373427;          // RD of 1 Tishri AM 1
const RD_UNIX_EPOCH = 719163;           // RD of 1970-01-01
function hElapsedDays(y){
  const monthsElapsed = Math.floor((235*y - 234)/19);
  const partsElapsed = 12084 + 13753*monthsElapsed;
  let day = 29*monthsElapsed + Math.floor(partsElapsed/25920);
  if ((3*(day+1)) % 7 < 3) day += 1;
  return day;
}
function hYearCorrection(y){
  const ny0 = hElapsedDays(y-1), ny1 = hElapsedDays(y), ny2 = hElapsedDays(y+1);
  if (ny2 - ny1 === 356) return 2;
  if (ny1 - ny0 === 382) return 1;
  return 0;
}
function hNewYearRD(y){ return HEBREW_EPOCH + hElapsedDays(y) + hYearCorrection(y); }
function rdToISO(rd){
  const d = new Date((rd - RD_UNIX_EPOCH) * 86400000);
  return d.toISOString().slice(0,10);
}
// Returns { "YYYY-MM-DD": kind } for Hebrew year y.
// kind: 'chag' (no daytime service), 'erev' (Friday-style hours), 'erevYK', 'YK', 'erevPesach'
function holidaysForHebrewYear(y){
  const rh = hNewYearRD(y);
  const pesach = hNewYearRD(y+1) - 163;      // 15 Nisan
  const out = {};
  const put = (rd, kind, name) => { out[rdToISO(rd)] = {kind, name}; };
  put(rh-1, 'erev', 'Erev Rosh Hashana');
  put(rh,   'chag', 'Rosh Hashana I');
  put(rh+1, 'chag', 'Rosh Hashana II');
  put(rh+8, 'erevYK', 'Erev Yom Kippur');
  put(rh+9, 'YK', 'Yom Kippur');
  put(rh+13,'erev', 'Erev Sukkot');
  put(rh+14,'chag', 'Sukkot');
  put(rh+20,'erev', 'Hoshana Raba');
  put(rh+21,'chag', 'Shmini Atzeret / Simchat Torah');
  put(pesach-1,'erevPesach','Erev Pesach');
  put(pesach,  'chag', 'Pesach I');
  put(pesach+5,'erev', 'Erev Shvi\'i shel Pesach');
  put(pesach+6,'chag', 'Shvi\'i shel Pesach');
  put(pesach+49,'erev','Erev Shavuot');
  put(pesach+50,'chag','Shavuot');
  return out;
}
function holidayTable(fromHY, toHY){
  let t = {};
  for (let y=fromHY; y<=toHY; y++) Object.assign(t, holidaysForHebrewYear(y));
  return t;
}
if (typeof module !== 'undefined') module.exports = {holidaysForHebrewYear, holidayTable, hNewYearRD, rdToISO};
