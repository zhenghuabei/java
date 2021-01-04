/**
 * Created by PanJiaChen on 16/11/18.
 */
const baseAttr = {
  min: "%s最小长度为:min",
  max: "%s最大长度为:max",
  length: "%s长度必须为:length",
  range: "%s长度为:range",
  pattern: "$s格式错误"
};

/**
 * @param {string} path
 * @returns {Boolean}
 */
export function isExternal(path) {
  return /^(https?:|mailto:|tel:)/.test(path)
}

/**
 * @param {string} str
 * @returns {Boolean}
 */
export function validUsername(str) {
  const valid_map = ['admin', 'editor']
  return valid_map.indexOf(str.trim()) >= 0
}

/**
 * @param {string} url
 * @returns {Boolean}
 */
export function validURL(url) {
  const reg = /^(https?|ftp):\/\/([a-zA-Z0-9.-]+(:[a-zA-Z0-9.&%$-]+)*@)*((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}|([a-zA-Z0-9-]+\.)*[a-zA-Z0-9-]+\.(com|edu|gov|int|mil|net|org|biz|arpa|info|name|pro|aero|coop|museum|[a-zA-Z]{2}))(:[0-9]+)*(\/($|[a-zA-Z0-9.,?'\\+&%$#=~_-]+))*$/
  return reg.test(url)
}

/**
 * @param {string} str
 * @returns {Boolean}
 */
export function validLowerCase(str) {
  const reg = /^[a-z]+$/
  return reg.test(str)
}

/**
 * @param {string} str
 * @returns {Boolean}
 */
export function validUpperCase(str) {
  const reg = /^[A-Z]+$/
  return reg.test(str)
}

/**
 * @param {string} str
 * @returns {Boolean}
 */
export function validAlphabets(str) {
  const reg = /^[A-Za-z]+$/
  return reg.test(str)
}

/**
 * @param {string} email
 * @returns {Boolean}
 */
export function validEmail(email) {
  // eslint-disable-next-line no-useless-escape
  const reg = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/
  return reg.test(email)
}

/**
 * @param {string} str
 * @returns {Boolean}
 */
export function isString(str) {
  if (typeof str === 'string' || str instanceof String) {
    return true
  }
  return false
}

/**
 * @param {Array} arg
 * @returns {Boolean}
 */
export function isArray(arg) {
  if (typeof Array.isArray === 'undefined') {
    return Object.prototype.toString.call(arg) === '[object Array]'
  }
  return Array.isArray(arg)
}

const bindMessage = (fn, message) => {
  fn.message = field => message.replace("%s", field || "");
};

export function required(message, opt = {}) {
  return {
    required: true,
    message,
    type: "string",
    ...opt
  };
}
bindMessage(required, "请输入%s");

/**
 * 正确的金额
 *
 * @param message
 * @returns {*}
 */
export function num(message) {
  return attrs.pattern(
    /(^[1-9]([0-9]+)?(\.[0-9]{1,2})?$)|(^(0){1}$)|(^[0-9]\.[0-9]([0-9])?$)/,
    message
  );
}
bindMessage(num, "%s格式不正确");


const attrs = Object.keys(baseAttr).reduce((attrs, key) => {
  attrs[key] = (attr, message = "", opt = {}) => {
    const _attr =
      key === "range" ? { min: attr[0], max: attr[1] } : { [key]: attr };

    return {
      message: message.replace(
        `:${key}`,
        key === "range" ? `${attr[0]}-${attr[1]}` : attr
      ),
      type: "string",
      ..._attr,
      ...opt
    };
  };
  bindMessage(attrs[key], baseAttr[key]);
  return attrs;
}, {});
export default attrs;
