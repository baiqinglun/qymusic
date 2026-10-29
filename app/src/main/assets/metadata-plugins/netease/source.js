/* QYMusic 内置元数据插件：网易云音乐（与 Lyrico 插件格式一致） */

var HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36',
  'Referer': 'https://music.163.com/'
};

function __esc(value) { return encodeURIComponent(String(value == null ? '' : value)); }

function __httpJson(url) {
  return JSON.parse(Platform.http.getText(url, { headers: HEADERS }));
}

function __joinArtists(list) {
  if (!list || !list.length) return '';
  var names = [];
  for (var i = 0; i < list.length; i++) {
    var item = list[i];
    names.push(typeof item === 'string' ? item : (item && item.name) || '');
  }
  return names.filter(function (name) { return !!name; }).join(' / ');
}

function __searchSongs(keyword, page, pageSize) {
  var offset = (Math.max(1, page) - 1) * pageSize;
  var data = __httpJson(
    'https://music.163.com/api/search/get/web?s=' + __esc(keyword) +
    '&type=1&offset=' + offset + '&limit=' + pageSize
  );
  var songs = (data && data.result && data.result.songs) || [];
  var out = [];
  for (var i = 0; i < songs.length; i++) {
    var song = songs[i];
    out.push({
      id: String(song.id),
      title: song.name || '',
      artist: __joinArtists(song.artists),
      album: (song.album && song.album.name) || '',
      duration: song.duration || 0,
      picUrl: ''
    });
  }
  return out;
}

function __bestMatch(candidates, title, artist, durationMs) {
  var best = null;
  var bestScore = -1;
  var lowerTitle = String(title || '').toLowerCase();
  var lowerArtist = String(artist || '').toLowerCase();
  for (var i = 0; i < candidates.length; i++) {
    var item = candidates[i];
    var itemTitle = String(item.name || item.title || '').toLowerCase();
    var itemArtist = __joinArtists(item.artists || item.singers).toLowerCase();
    var score = 0;
    if (itemTitle && lowerTitle.indexOf(itemTitle) >= 0) score += 2;
    if (itemTitle && itemTitle.indexOf(lowerTitle) >= 0) score += 2;
    if (itemArtist && lowerArtist.indexOf(itemArtist) >= 0) score += 2;
    if (i === 0) score += 1;
    var itemDuration = item.duration || 0;
    if (durationMs > 0 && itemDuration > 0) {
      var diff = Math.abs(itemDuration - durationMs);
      if (diff <= 3000) score += 3;
      else if (diff <= 8000) score += 1;
      else if (diff > 20000) score -= 2;
    }
    if (score > bestScore) {
      bestScore = score;
      best = item;
    }
  }
  return best;
}

function __centis(tag) {
  var m = String(tag).match(/\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/);
  if (!m) return -1;
  var frac = m[3] ? parseInt(m[3], 10) : 0;
  if (String(m[3] || '').length >= 3) frac = Math.round(frac / 10);
  return parseInt(m[1], 10) * 6000 + parseInt(m[2], 10) * 100 + frac;
}

function __mergeTranslation(original, translation) {
  var trans = {};
  String(translation || '').split(/\r?\n/).forEach(function (line) {
    var tags = line.match(/^\s*((?:\[[^\]]+\])+)/);
    if (!tags) return;
    var text = line.replace(/^\s*(?:\[[^\]]+\])+/, '').trim();
    if (!text) return;
    tags[1].match(/\[[^\]]+\]/g).forEach(function (tag) {
      var key = __centis(tag);
      if (key < 0) return;
      if (!trans[key]) trans[key] = [];
      trans[key].push(text);
    });
  });
  var out = [];
  String(original || '').split(/\r?\n/).forEach(function (line) {
    out.push(line);
    var tags = line.match(/^\s*((?:\[[^\]]+\])+)/);
    if (!tags) return;
    var body = line.replace(/^\s*(?:\[[^\]]+\])+/, '').trim();
    if (!body) return;
    tags[1].match(/\[[^\]]+\]/g).forEach(function (tag) {
      var key = __centis(tag);
      var list = key >= 0 ? trans[key] : null;
      if (list) list.forEach(function (text) { out.push(tag + text); });
    });
  });
  return out.join('\n');
}

function __pickSong(request) {
  var song = request.song || {};
  var keyword = [song.title, song.artist].filter(function (part) { return !!part; }).join(' ');
  var candidates = __searchSongs(keyword || song.title || '', 1, 10);
  return __bestMatch(candidates, song.title, song.artist, song.duration || 0);
}

function searchSongs(request) {
  var pageSize = request.pageSize || 20;
  return __searchSongs(request.keyword || '', request.page || 1, pageSize);
}

function getLyrics(request) {
  var song = request.song || {};
  var match = __pickSong(request);
  if (!match) return [];
  var lyric = __httpJson(
    'https://music.163.com/api/song/lyric?id=' + __esc(match.id) + '&lv=1&kv=1&tv=-1'
  );
  var original = (lyric && lyric.lrc && lyric.lrc.lyric) || '';
  if (!original) return [];
  var merged = __mergeTranslation(original, (lyric.tlyric && lyric.tlyric.lyric) || '');
  return [{
    type: 'rawPlainLrc',
    tags: { ti: match.title || song.title || '', ar: match.artist || song.artist || '', al: match.album || song.album || '' },
    rawPlainLrc: merged
  }];
}

function searchCovers(request) {
  var match = __pickSong(request);
  if (!match) return [];
  var detail = __httpJson(
    'https://music.163.com/api/song/detail?id=' + __esc(match.id) +
    '&ids=%5B' + __esc(match.id) + '%5D'
  );
  var songs = (detail && detail.songs) || [];
  var picUrl = songs[0] && songs[0].album && songs[0].album.picUrl;
  if (!picUrl) return [];
  return [{ url: picUrl + '?param=1200y1200', width: 1200, height: 1200 }];
}
