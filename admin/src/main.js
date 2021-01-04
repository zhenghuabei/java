import Vue from 'vue'

import Cookies from 'js-cookie'

import 'normalize.css/normalize.css' // a modern alternative to CSS resets

import Element from 'element-ui'
import './styles/element-variables.scss'

import '@/styles/index.scss' // global css

import "@/assets/iconfont/iconfont";
import "@/assets/iconfont/iconfont.css";

import VueAwesomeSwiper from 'vue-awesome-swiper'
import 'swiper/dist/css/swiper.css'
import "vue-ydui/dist/ydui.base.css";
// 懒加载
import VueLazyload from 'vue-lazyload'

Vue.config.devtools=true
import App from './App'
import store from './store'
import router from './router'
import attrFrom from './components/attrFrom'
import uploadPicture from './components/uploadPicture/uploadFrom'
import goodListFrom from './components/goodList/goodListFrom'
import couponFrom from './components/couponList/couponFrom'
import articleFrom from './components/articleList/articleFrom'
import UploadIndex from '@/components/uploadPicture/index.vue'
import UploadFile from '@/components/Upload/uploadFile.vue'
import ueditorFrom from '@/components/ueditorFrom'
import VueUeditorWrap from 'vue-ueditor-wrap'
import iconFrom from './components/iconFrom'
import TimeSelect from '@/components/TimeSelect'
import dialog from "@/libs/dialog";
import scroll from "@/libs/loading";
import schema from "async-validator";
// 切勿更改 此组件为表单生成中使用的图片上传组件
import SelfUpload from '@/components/uploadPicture/forGenrator/index.vue'
import modalAttr from '@/libs/modal-attr'
import modalIcon from '@/libs/modal-icon'
import { modalSure } from '@/libs/public'
import { loadScriptQueue } from '@/components/FormGenerator/utils/loadScript'

import './icons' // icon
import './permission' // permission control
import './utils/error-log' // error integralLog
import * as filters from './filters' // global filters
import { parseQuery } from "@/utils";
import * as Auth from '@/libs/wechat';
Vue.use(VueLazyload, {
  preLoad: 1.3,
  error: require('./assets/imgs/no.png'),
  loading: require('./assets/imgs/moren.jpg'),
  attempt: 1,
  listenEvents: ['scroll', 'wheel', 'mousewheel', 'resize', 'animationend', 'transitionend', 'touchmove']
})

Vue.use(uploadPicture)
Vue.use(goodListFrom)
Vue.use(couponFrom)
Vue.use(articleFrom)
Vue.use(VueAwesomeSwiper)
Vue.component('vue-ueditor-wrap', VueUeditorWrap)
Vue.component('attrFrom', attrFrom)
Vue.component('UploadIndex', UploadIndex)
Vue.component('SelfUpload', SelfUpload)
Vue.component('iconFrom', iconFrom)
Vue.component('ueditorFrom', ueditorFrom)
Vue.component('uploadFile', UploadFile)
Vue.component('timeSelect', TimeSelect)
Vue.prototype.$modalSure = modalSure
Vue.prototype.$modalAttr = modalAttr
Vue.prototype.$modalIcon = modalIcon
Vue.prototype.$dialog = dialog
Vue.prototype.$scroll = scroll;
Vue.prototype.$wechat = Auth;
Vue.prototype.$validator = function(rule) {
  return new schema(rule);
};

let cookieName = "VCONSOLE";
let query = parseQuery();
let urlSpread = query["spread"];
let vconsole = query[cookieName.toLowerCase()];
let md5Crmeb = "b14d1e9baeced9bb7525ab19ee35f2d2"; //CRMEB MD5 加密开启vconsole模式
let md5UnCrmeb = "3dca2162c4e101b7656793a1af20295c"; //UN_CREMB MD5 加密关闭vconsole模式


if (vconsole !== undefined) {
  if (vconsole === md5UnCrmeb && Cookies.has(cookieName))
    Cookies.remove(cookieName);
} else vconsole = Cookies.get(cookieName);

if (vconsole !== undefined && vconsole === md5Crmeb) {
  Cookies.set(cookieName, md5Crmeb, 3600);
  const module = () => import("vconsole");
  module().then(Module => {
    new Module.default();
  });
}
// Vue.prototype.$modalCoupon = modalCoupon
/**
 * If you don't want to use mock-server
 * you want to use MockJs for mock api
 * you can execute: mockXHR()
 *
 * Currently MockJs will be used in the production environment,
 * please remove it before going online ! ! !
 */
if (process.env.NODE_ENV === 'production') {
  const { mockXHR } = require('../mock')
  mockXHR()
}

Vue.use(Element, {
  size: Cookies.get('size') || 'mini' // set element-ui default size
})

// register global utility filters
Object.keys(filters).forEach(key => {
  Vue.filter(key, filters[key])
})

Vue.config.productionTip = false

const $previewApp = document.getElementById('previewApp')
const childAttrs = {
  file: '',
  dialog: ' width="600px" class="dialog-width" v-if="visible" :visible.sync="visible" :modal-append-to-body="false" '
}

window.addEventListener('message', init, false)

function buildLinks(links) {
  let strs = ''
  links.forEach(url => {
    strs += `<link href="${url}" rel="stylesheet">`
  })
  return strs
}

function init(event) {
  if (event.data.type === 'refreshFrame') {
    const code = event.data.data
    const attrs = childAttrs[code.generateConf.type]
    let links = ''

    if (Array.isArray(code.links) && code.links.length > 0) {
      links = buildLinks(code.links)
    }

    $previewApp.innerHTML = `${links}<style>${code.css}</style><div id="app"></div>`

    if (Array.isArray(code.scripts) && code.scripts.length > 0) {
      loadScriptQueue(code.scripts, () => {
        newVue(attrs, code.js, code.html)
      })
    } else {
      newVue(attrs, code.js, code.html)
    }
  }
}

function newVue(attrs, main, html) {
  // eslint-disable-next-line no-eval
  main = eval(`(${main})`)
  main.template = `<div>${html}</div>`
  new Vue({
    components: {
      child: main
    },
    data() {
      return {
        visible: true
      }
    },
    template: `<div><child ${attrs}/></div>`
  }).$mount('#app')
}

new Vue({
  el: '#app',
  router,
  store,
  render: h => h(App)
})
