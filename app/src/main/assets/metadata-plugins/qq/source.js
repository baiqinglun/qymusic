/* QYMusic 内置元数据插件：QQ音乐 */

var UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36';
var REFERER = 'https://y.qq.com/';

function __esc(value) { return encodeURIComponent(String(value == null ? '' : value)); }

function __httpJson(url) {
  return JSON.parse(Platform.http.getText(url, {
    headers: { 'User-Agent': UA, 'Referer': REFERER }
  }));
}

/* 单个端点失败（HTTP 500 / 风控空响应等）不应拖垮整条回退链 */
function __tryJson(step) {
  try {
    return step();
  } catch (error) {
    Platform.log.warn('qq', String((error && error.message) || error));
    return null;
  }
}

function __clean(value) {
  return String(value == null ? '' : value)
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, ' ')
    .trim();
}

function __coverByAlbumMid(albumMid) {
  var mid = __clean(albumMid);
  if (!mid) return '';
  return 'https://y.gtimg.cn/music/photo_new/T002R500x500M000' + mid + '.jpg';
}

function __searchMusicu(keyword, page, pageSize) {
  var payload = JSON.stringify({
    'music.search.SearchCgiService': {
      method: 'DoSearchForQQMusicDesktop',
      module: 'music.search.SearchCgiService',
      param: {
        search_type: 0,
        query: String(keyword || ''),
        page_num: page || 1,
        num_per_page: pageSize || 20
      }
    }
  });
  var data = __httpJson('https://u.y.qq.com/cgi-bin/musicu.fcg?data=' + __esc(payload));
  var service = (data && data['music.search.SearchCgiService']) || {};
  var list = (((service.data || {}).body || {}).song || {}).list || [];
  return list.map(function (item) {
    var singers = (item.singer || [])
      .map(function (singer) { return __clean(singer && singer.name); })
      .filter(function (name) { return !!name; });
    var album = item.album || {};
    return {
      id: String(item.mid || item.id || ''),
      title: __clean(item.name || item.songname),
      artist: singers.join('/'),
      album: __clean(album.name || ''),
      duration: (parseInt(item.interval, 10) || 0) * 1000,
      picUrl: __coverByAlbumMid(album.mid)
    };
  }).filter(function (item) { return !!item.id; });
}

function __searchClientSearch(keyword, page, pageSize) {
  var data = __httpJson(
    'https://c.y.qq.com/soso/fcgi-bin/client_search_cp?' +
    'w=' + __esc(keyword) +
    '&format=json&n=' + (pageSize || 20) + '&p=' + (page || 1) +
    '&cr=1&g_tk=5381&loginUin=0&hostUin=0&platform=yqq.json&needNewCode=0'
  );
  var list = (data && data.data && data.data.song && data.data.song.list) || [];
  return list.map(function (item) {
    var singers = (item.singer || [])
      .map(function (singer) { return __clean(singer && singer.name); })
      .filter(function (name) { return !!name; });
    return {
      id: String(item.songmid || item.songid || ''),
      title: __clean(item.songname),
      artist: singers.join('/') || __clean(item.singername || ''),
      album: __clean(item.albumname || ''),
      duration: (parseInt(item.interval, 10) || 0) * 1000,
      picUrl: __coverByAlbumMid(item.albummid)
    };
  }).filter(function (item) { return !!item.id; });
}

function __searchSmartbox(keyword) {
  var data = __httpJson(
    'https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?key=' + __esc(keyword) + '&format=json'
  );
  var list = (data && data.data && data.data.song && data.data.song.itemlist) || [];
  return list.map(function (item) {
    return {
      id: String(item.mid || ''),
      title: __clean(item.name),
      artist: __clean(item.singer),
      album: '',
      duration: 0,
      picUrl: __coverByAlbumMid(item.albumMid)
    };
  }).filter(function (item) { return !!item.id; });
}

/* 主搜索走 musicu.fcg；被风控/无结果/报错时依次回退旧搜索接口与 smartbox 联想 */
function __search(keyword, page, pageSize) {
  var result = __tryJson(function () { return __searchMusicu(keyword, page, pageSize); }) || [];
  if (!result.length) {
    result = __tryJson(function () { return __searchClientSearch(keyword, page, pageSize); }) || [];
  }
  if (!result.length) {
    result = __tryJson(function () { return __searchSmartbox(keyword); }) || [];
  }
  return result;
}

function __bestMatch(candidates, title, artist, durationMs) {
  var best = null;
  var bestScore = -1;
  var lowerTitle = String(title || '').toLowerCase();
  var lowerArtist = String(artist || '').toLowerCase();
  for (var i = 0; i < candidates.length; i++) {
    var item = candidates[i];
    var itemTitle = String(item.title || '').toLowerCase();
    var itemArtist = String(item.artist || '').toLowerCase();
    var score = 0;
    if (itemTitle && lowerTitle.indexOf(itemTitle) >= 0) score += 2;
    if (itemTitle && itemTitle.indexOf(lowerTitle) >= 0) score += 2;
    if (itemArtist && (lowerArtist.indexOf(itemArtist) >= 0 || itemArtist.indexOf(lowerArtist) >= 0)) score += 2;
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

function __pickSong(request) {
  var song = request.song || {};
  var keyword = [song.title, song.artist].filter(function (part) { return !!part; }).join(' ');
  var candidates = __search(keyword || song.title || '', 1, 10);
  return __bestMatch(candidates, song.title, song.artist, song.duration || 0);
}

function searchSongs(request) {
  return __search(request.keyword || '', request.page || 1, request.pageSize || 20);
}

function getLyrics(request) {
  var song = request.song || {};
  var match = __pickSong(request);
  if (!match) return [];
  var data = __tryJson(function () {
    return __httpJson(
      'https://i.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?' +
      'songmid=' + __esc(match.id) +
      '&g_tk=5381&format=json&nobase64=1&loginUin=0&hostUin=0' +
      '&platform=yqq.json&needNewCode=0'
    );
  });
  if (!data) return [];
  var lyric = String((data && data.lyric) || '').trim();
  if (!lyric) return [];
  /* 个别响应会忽略 nobase64，拿到的还是 base64：内容里没有 LRC 时间轴标记就尝试解码 */
  if (lyric.indexOf('[') < 0) {
    var decoded = Platform.base64.decodeText(lyric);
    if (decoded && decoded.indexOf('[') >= 0) lyric = decoded.trim();
  }
  if (!lyric) return [];
  return [{
    type: 'rawPlainLrc',
    tags: { ti: match.title || song.title || '', ar: match.artist || song.artist || '', al: match.album || song.album || '' },
    rawPlainLrc: lyric
  }];
}

function searchCovers(request) {
  var song = request.song || {};
  var match = __pickSong(request);
  var cover = (match && match.picUrl) || __coverByAlbumMid(song.internal && song.internal.albumMid) || '';
  if (!cover) return [];
  return [
    { url: cover, width: 500 },
    { url: cover.replace('R500x500M000', 'R300x300M000'), width: 300 }
  ];
}
