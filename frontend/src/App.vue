<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import QRCode from 'qrcode'
import Icon from './components/Icon.vue'
import { api, money, dateTime, states } from './api'

const user = ref(null)
const page = ref('catalog')
const currentPage = ref(1)
const total = ref(0)
const pageSize = 12
const loading = ref(false)
const actionBusy = ref(false)
const pageError = ref('')
const toast = ref('')
let toastTimer
const notify = text => { toast.value = text; clearTimeout(toastTimer); toastTimer = setTimeout(() => toast.value = '', 4500) }
const products = ref([]), stores = ref([]), cart = ref([]), orders = ref([]), records = ref([]), applications = ref([]), appointments = ref([]), slots = ref([]), audits = ref([])
const stats = ref({}), profile = ref(null)
const keyword = ref(''), category = ref(''), statusFilter = ref(''), appointmentDate = ref('')
const cartCount = ref(0), selected = ref([]), selectedStore = ref('')
const modal = ref(''), form = ref({}), active = ref(null), authMode = ref('login'), authError = ref('')
const bookingSlots = ref([]), bookingDate = ref(''), selectedSlot = ref('')
const verified = ref(null), voucherCode = ref(''), arrivalCode = ref(''), qrImage = ref(''), qrBase = ref(window.location.origin)
const scanStore = ref(Number(new URLSearchParams(window.location.search).get('checkin')) || null)
const reviewRows = ref([])
const roleName = computed(() => ({ CUSTOMER: '车主空间', ADMIN: '平台管理', STORE: '门店工作台' }[user.value?.role] || '车主空间'))
const navigation = computed(() => user.value?.role === 'ADMIN' ? [
  ['overview', '运营概览', 'grid'], ['products', '商品与库存', 'bag'], ['store-review', '加盟店审核', 'store'], ['order-review', '订单审核', 'orders'], ['audit', '操作日志', 'shield']
] : user.value?.role === 'STORE' ? [
  ['overview', '门店概览', 'grid'], ['checkin', '到店核验', 'qr'], ['appointments', '预约安排', 'calendar'], ['service-records', '保养服务', 'wrench'], ['slots', '时段管理', 'clock']
] : [
  ['catalog', '保养商城', 'grid'], ['cart', '购物车', 'bag'], ['orders', '我的订单', 'orders'], ['records', '保养记录', 'wrench'], ['stores', '服务门店', 'pin']
])
const pageTitle = computed(() => ({ arrival: '扫码到店登记', ...Object.fromEntries(navigation.value.map(x => [x[0], x[1]])) }[page.value] || '车途养护'))
const subtitle = computed(() => ({catalog:'为每一次出发，做好准备。',cart:'选好配件，预约一家值得信赖的门店。',orders:'从线上选购到到店保养，进度清晰可见。',records:'每一次用心养护，都有记录可循。',stores:'专业服务就在身边。',overview:'今日业务，一目了然。',products:'维护商品、工时费与平台库存。','store-review':'审核加盟申请，连接专业服务。','order-review':'确认订单并配送配件到指定门店。',audit:'关键业务操作，清晰留痕。',checkin:'核对数字凭据，开始到店服务。',appointments:'查看本店预约与到店登记。','service-records':'登记服务内容，留存完整保养记录。',slots:'设置未来31天的服务时段与容量。',arrival:'输入订单凭据，完成到店登记。'}[page.value]))
const selectedRows = computed(() => cart.value.filter(x => selected.value.includes(x.cartItem.id)))
const amounts = computed(() => selectedRows.value.reduce((t,x) => ({goods:t.goods + x.product.price*x.cartItem.quantity,labor:t.labor + x.product.laborFee*x.cartItem.quantity}),{goods:0,labor:0}))
const bookDates = computed(() => [...new Set(bookingSlots.value.map(x => x.serviceDate))])
const dateSlots = computed(() => bookingSlots.value.filter(x => x.serviceDate === bookingDate.value))
const today = new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date())
const readableDate = new Intl.DateTimeFormat('zh-CN',{month:'long',day:'numeric',weekday:'long',timeZone:'Asia/Shanghai'}).format(new Date())
const menuActive = id => page.value === id
const isConsumer = computed(() => !user.value || user.value.role === 'CUSTOMER')
const headerSearch = ref('')
const mainNavigation = computed(() => isConsumer.value
  ? [['catalog','保养商城','grid'],['stores','服务门店','pin'],['orders','我的订单','orders'],['records','保养记录','wrench']]
  : navigation.value)
const featuredSkus = ['OIL-001','FILTER-001','AIR-001','BRAKE-001','BATTERY-001','WIPER-001','COOL-001','CABIN-001']
const catalogProducts = computed(() => [...products.value].sort((a,b) => {
  const rank = sku => { const i = featuredSkus.indexOf(sku); return i < 0 ? featuredSkus.length : i }
  return rank(a.product.sku) - rank(b.product.sku)
}))
const serviceCategories = [
  { label:'机油保养', category:'润滑油', image:'oil.webp' },
  { label:'滤芯更换', category:'滤清器', image:'oil-filter.webp' },
  { label:'制动养护', category:'制动系统', image:'brake-pads.webp' },
  { label:'电瓶更换', category:'电气系统', image:'battery.webp' },
  { label:'门店预约', page:'stores', icon:'store' }
]
const productAssets = {
  oil:'oil.webp', filter:'oil-filter.webp', air:'air-filter.webp', brake:'brake-pads.webp',
  battery:'battery.webp', wiper:'wipers.webp', coolant:'coolant.webp', cabin:'cabin-filter.webp'
}
function productImage(product) {
  const image = product.imageUrl || ''
  const legacy = image.match(/^\/assets\/([\w-]+)\.svg$/)
  if (legacy) {
    const key = product.sku === 'CABIN-001' ? 'cabin' : legacy[1]
    if (productAssets[key]) return '/assets/redesign/' + productAssets[key]
  }
  return image || '/assets/redesign/oil.webp'
}
function imageFallback(event) {
  if (!event.target.src.endsWith('/assets/redesign/oil.webp')) event.target.src = '/assets/redesign/oil.webp'
}
async function chooseCategory(value = '') {
  keyword.value = ''; headerSearch.value = ''; currentPage.value = 1
  if (category.value === value) await load()
  else category.value = value
  await nextTick()
  document.getElementById('catalog-products')?.scrollIntoView({ behavior:'smooth', block:'start' })
}
function searchCatalog() {
  page.value = 'catalog'; currentPage.value = 1; keyword.value = headerSearch.value.trim()
  if (category.value) category.value = ''
  else load()
}
function chooseService(service) {
  if (service.page) navigate(service.page)
  else chooseCategory(service.category)
}
function openAuth(mode = 'login') { authMode.value = mode; authError.value = ''; form.value = {loginName:'',password:'',phone:'',name:'',contactName:'',contactPhone:'',address:'',businessHours:'09:00–18:00'}; modal.value='auth' }
function navigate(id) {
  if (!user.value && !['catalog','stores','arrival'].includes(id)) { openAuth(); return }
  page.value = id; currentPage.value = 1; keyword.value = ''; headerSearch.value = ''; statusFilter.value = ''; verified.value = null; load()
}
async function run(action) {
  if (actionBusy.value) return
  actionBusy.value = true
  try { await action() } catch (e) { notify(e.message) } finally { actionBusy.value = false }
}
async function refreshCartCount() {
  if (user.value?.role === 'CUSTOMER') { const rows = await api('/cart'); cartCount.value = rows.reduce((sum,x)=>sum+x.cartItem.quantity,0) } else cartCount.value = 0
}
async function load() {
  loading.value = true; pageError.value=''
  const params = new URLSearchParams({page:currentPage.value,size:pageSize})
  if (keyword.value) params.set('keyword',keyword.value)
  if (statusFilter.value) params.set('status',statusFilter.value)
  try {
    let result
    if (page.value === 'catalog' || page.value === 'products') {
      if(category.value && page.value === 'catalog') params.set('category',category.value)
      result = await api((page.value === 'products' ? '/admin/products?' : '/products?') + params); products.value=result.records
    } else if (['stores','arrival'].includes(page.value)) { result=await api('/stores?'+params); stores.value=result.records }
    else if (page.value === 'cart') {
      const result = await Promise.all([api('/cart'),api('/stores?size=100')]); cart.value=result[0]; stores.value=result[1].records
      selected.value=cart.value.filter(x=>x.product.status==='ON_SHELF').map(x=>x.cartItem.id)
      if(!selectedStore.value || !stores.value.some(s=>s.id===Number(selectedStore.value))) selectedStore.value=stores.value[0]?.id || ''
    } else if (['orders','order-review'].includes(page.value)) { result=await api((page.value==='orders'?'/orders/mine?':'/admin/orders?')+params); orders.value=result.records }
    else if (['records','service-records'].includes(page.value)) { result=await api((page.value==='records'?'/maintenance-records/mine?':'/store/maintenance-records?')+params); records.value=result.records }
    else if(page.value==='store-review') { result=await api('/admin/stores?'+params); applications.value=result.records }
    else if(page.value==='audit') { result=await api('/admin/audit-logs?'+params); audits.value=result.records }
    else if(page.value==='appointments') { if(appointmentDate.value) params.set('date',appointmentDate.value); result=await api('/store/appointments?'+params); appointments.value=result.records }
    else if(page.value==='slots') slots.value=await api('/store/slots')
    else if(page.value==='checkin') { profile.value=await api('/store/profile'); await makeQr() }
    else if(page.value==='overview') {
      if(user.value.role==='ADMIN') {
        const [a,b,c]=await Promise.all([api('/admin/dashboard'),api('/admin/orders/pending?size=5'),api('/admin/stores/pending?size=5')]); stats.value=a; orders.value=b.records; applications.value=c.records
      } else {
        const [a,b,c]=await Promise.all([api('/store/profile'),api('/store/appointments?size=5'),api('/store/maintenance-records?status=IN_PROGRESS&size=5')]); profile.value=a; appointments.value=b.records; records.value=c.records; stats.value={appointments:b.total,inProgress:c.total}
      }
    }
    total.value=result?.total || 0
    await refreshCartCount()
  } catch(e) { pageError.value=e.message } finally { loading.value=false }
}
async function authenticate() {
  authError.value=''
  await run(async()=>{
    try {
      if(authMode.value==='login') {
        const result=await api('/auth/login','POST',{loginName:form.value.loginName,password:form.value.password})
        user.value=result.user; sessionStorage.setItem('maintenance_token',result.token); sessionStorage.setItem('maintenance_user',JSON.stringify(result.user)); modal.value=''
        navigate(result.user.role==='CUSTOMER'?(scanStore.value?'arrival':'catalog'):'overview'); notify('登录成功')
      } else {
        const result=await api('/auth/register/'+(authMode.value==='customer'?'customer':'store'),'POST',form.value)
        notify(result.message); authMode.value='login'; form.value.password=''
      }
    } catch(e) { authError.value=e.message }
  })
}
function logout() { sessionStorage.removeItem('maintenance_token');sessionStorage.removeItem('maintenance_user');user.value=null;cartCount.value=0;modal.value='';navigate('catalog');notify('已退出登录') }
function demoLogin(role) { const accounts={CUSTOMER:['customer','User@123'],ADMIN:['admin','Admin@123'],STORE:['store','Store@123']}; [form.value.loginName,form.value.password]=accounts[role] }
function addToCart(product) { if(!user.value){openAuth();return} run(async()=>{await api('/cart/items','POST',{productId:product.id,quantity:1});await refreshCartCount();notify('已加入购物车')}) }
function changeQuantity(row,delta) { const quantity=row.cartItem.quantity+delta;if(quantity<1||quantity>99)return;run(async()=>{await api('/cart/items/'+row.cartItem.id,'PUT',{quantity});await load()}) }
function removeCart(row) { run(async()=>{await api('/cart/items/'+row.cartItem.id,'DELETE');await load();notify('已移出购物车')}) }
function checkout() { if(!selectedRows.value.length||!selectedStore.value){notify('请选择商品和服务门店');return} modal.value='checkout' }
function submitOrder() { run(async()=>{await api('/orders','POST',{cartItemIds:selected.value,storeId:Number(selectedStore.value)});modal.value='';navigate('orders');notify('订单已提交，等待平台审核')}) }
async function openBook(view) { active.value=view; await run(async()=>{bookingSlots.value=await api('/slots?storeId='+view.order.storeId);bookingDate.value=bookDates.value[0]||'';selectedSlot.value='';modal.value='booking'}) }
function book() { run(async()=>{await api('/appointments','POST',{orderId:active.value.order.id,slotId:Number(selectedSlot.value)});modal.value='';await load();notify('预约成功，请在预约当天到店')}) }
function showOrder(view) { active.value=view;modal.value='order' }
function openDecision(type,view,decision) { active.value=view;form.value={type,decision,comment:''};modal.value='decision' }
function decide() { run(async()=>{await api('/admin/'+(form.value.type==='store'?'stores/':'orders/')+(form.value.type==='store'?active.value.id:active.value.order.id)+'/review','POST',{decision:form.value.decision,comment:form.value.comment});modal.value='';await load();notify('审核结果已保存')}) }
function editProduct(row) { active.value=row;form.value=row?{...row.product,stock:row.stock}:{sku:'',name:'',category:'润滑油',description:'',imageUrl:'/assets/oil.svg',price:0,laborFee:0,status:'ON_SHELF',stock:0};modal.value='product' }
function saveProduct() { run(async()=>{await api('/admin/products'+(active.value?'/'+active.value.product.id:''),active.value?'PUT':'POST',form.value);modal.value='';await load();notify('商品信息已保存')}) }
function askDelete(row) { active.value=row;modal.value='delete' }
function deleteProduct() { run(async()=>{await api('/admin/products/'+active.value.product.id,'DELETE');modal.value='';await load();notify('商品已删除，历史订单已保留')}) }
function verify() { verified.value=null;run(async()=>{verified.value=await api('/store/check-ins/verify','POST',{code:voucherCode.value})}) }
function startService() { run(async()=>{await api('/store/check-ins/'+verified.value.voucherId+'/start','POST',{});verified.value=null;voucherCode.value='';navigate('service-records');notify('凭据已核销，保养服务已开始')}) }
function openComplete(row) { active.value=row;form.value={serviceItems:'',note:''};modal.value='complete' }
function complete() { run(async()=>{await api('/store/maintenance-records/'+active.value.record.id+'/complete','POST',form.value);modal.value='';await load();notify('服务已完成，保养记录已留存')}) }
function editSlot(slot) { active.value=slot;form.value=slot?{...slot}:{serviceDate:today,startTime:'10:00',endTime:'11:00',capacity:3,status:'OPEN'};modal.value='slot' }
function saveSlot() { run(async()=>{await api('/store/slots'+(active.value?'/'+active.value.id:''),active.value?'PUT':'POST',form.value);modal.value='';await load();notify('时段设置已保存')}) }
async function makeQr() { try { const base=new URL(qrBase.value); if(!['http:','https:'].includes(base.protocol))throw new Error();base.search='';base.hash='';base.searchParams.set('checkin',profile.value.id);qrImage.value=await QRCode.toDataURL(base.toString(),{width:240,margin:2,color:{dark:'#173e36',light:'#ffffff'}}) } catch {notify('请填写有效的http或https地址')} }
function arrive() { if(!user.value){openAuth();return} run(async()=>{await api('/check-ins','POST',{storeId:scanStore.value,code:arrivalCode.value});notify('到店登记成功，请等待门店服务人员核验');navigate('orders')}) }
function openReview(view) { active.value=view;form.value={orderId:view.order.id,targetType:'STORE',targetId:view.order.storeId,rating:5,content:''};modal.value='review' }
function reviewTarget(event) { const [type,id]=event.target.value.split(':');form.value.targetType=type;form.value.targetId=Number(id) }
function submitReview() {run(async()=>{await api('/reviews','POST',form.value);modal.value='';notify('感谢评价，你的反馈已公开展示')})}
function publicReviews(type,target) { active.value=target;run(async()=>{reviewRows.value=(await api('/reviews?targetType='+type+'&targetId='+target.id)).records;modal.value='reviews'}) }
async function copy(text) {try{await navigator.clipboard.writeText(text);notify('已复制')}catch{notify('浏览器不支持自动复制，请手动选中复制')}}
function paginate(delta) {currentPage.value+=delta;load();window.scrollTo({top:0,behavior:'smooth'})}
watch(category,()=>{if(page.value==='catalog'){currentPage.value=1;load()}})
watch(bookingDate,()=>selectedSlot.value='')
watch(modal,async value=>{if(value){await nextTick();document.querySelector('.modal input, .modal textarea, .modal select, .modal button')?.focus()}})
window.addEventListener('session-expired',()=>{sessionStorage.removeItem('maintenance_token');sessionStorage.removeItem('maintenance_user');user.value=null;cartCount.value=0;page.value=scanStore.value?'arrival':'catalog';openAuth()})
onMounted(async()=>{
  if(sessionStorage.getItem('maintenance_token'))try{user.value=await api('/auth/me')}catch{}
  page.value=scanStore.value?'arrival':user.value?.role==='ADMIN'||user.value?.role==='STORE'?'overview':'catalog'
  await load()
})
</script>

<template>
  <div class="app-shell" :class="{'staff-view':!isConsumer}">
    <header class="site-header">
      <div class="masthead content-width">
        <a class="brand" href="/" @click.prevent="navigate(isConsumer?'catalog':'overview')" aria-label="车途养护首页"><Icon name="car" :size="64" weight="bold"/><span>车途养护<span class="brand-sub">AUTOCARE</span></span></a>
        <form v-if="isConsumer" class="header-search" role="search" @submit.prevent="searchCatalog"><Icon name="search" :size="18"/><input v-model="headerSearch" aria-label="搜索商品" placeholder="搜索配件名称或编码"><button type="submit">搜索</button></form>
        <div v-else class="workspace-title"><span>{{roleName}}</span><small>{{readableDate}}</small></div>
        <div class="header-actions">
          <button v-if="!user" class="account-button" @click="openAuth()">登录 / 注册</button>
          <div v-else class="signed-in"><Icon name="user" :size="18"/><span>{{user.loginName}}</span><button class="icon-button" :aria-label="'退出账号'+user.loginName" :title="'退出账号 '+user.loginName" @click="logout"><Icon name="logout" :size="18"/></button></div>
          <button v-if="isConsumer" class="header-cart" :class="{active:page==='cart'}" @click="navigate('cart')"><Icon name="bag" :size="23"/><span>购物车</span><span v-if="cartCount" class="cart-count">{{cartCount}}</span></button>
        </div>
      </div>
      <div class="navigation-bar"><div class="content-width navigation-inner"><nav aria-label="主要导航"><button v-for="[id,label,icon] in mainNavigation" :key="id" :class="['nav-link',{active:menuActive(id)}]" :aria-current="menuActive(id)?'page':undefined" @click="navigate(id)"><Icon v-if="!isConsumer" :name="icon" :size="17"/><span>{{label}}</span></button></nav><span class="system-badge"><Icon name="headset" :size="19"/>{{pageError?'连接异常':'服务在线'}}<i></i></span></div></div>
    </header>
    <div class="main-shell">
      <main :class="{'catalog-layout':page==='catalog'}">
        <div v-if="page!=='catalog'" class="page-heading"><div><div class="eyebrow">{{user?.role==='ADMIN'?'PLATFORM MANAGEMENT':user?.role==='STORE'?'SERVICE WORKSPACE':'CARE FOR EVERY JOURNEY'}}</div><h1>{{pageTitle}}</h1><p>{{subtitle}}</p></div><button v-if="page==='products'" class="button" @click="editProduct(null)"><Icon name="plus"/> 新增商品</button><button v-if="page==='slots'" class="button" @click="editSlot(null)"><Icon name="plus"/> 新增时段</button><button v-if="!['catalog','products','slots','arrival'].includes(page)" class="button secondary" :disabled="loading" @click="load">刷新数据</button></div>
        <div v-if="pageError" class="error-banner" role="alert">{{pageError}}<button @click="load">重新加载</button></div>
        <div v-if="loading" class="loading-bar" aria-label="正在加载"></div>

        <template v-if="page==='catalog'">
          <section class="hero" aria-label="专业汽车养护"><div class="hero-media"><img src="/assets/redesign/hero-maintenance.webp" alt="机油、机油滤芯和空气滤芯的专业养护配件展示" fetchpriority="high"/></div><div class="hero-edge" aria-hidden="true"><img src="/assets/redesign/hero-maintenance.webp" alt=""/></div><div class="hero-content content-width"><div class="hero-copy"><h1>专业养护<br>让每一程更安心</h1><p>正品配件选购，专业门店服务。</p><div class="hero-actions"><button class="button" @click="chooseCategory('')">选购保养配件</button><button class="hero-link" @click="navigate('stores')">查看服务门店 <Icon name="arrow" :size="17"/></button></div></div></div></section>
          <div class="catalog-content content-width">
          <div class="care-benefits"><span><Icon name="shield" :size="27"/>配件与工时分开计价</span><span><Icon name="calendar" :size="27"/>线上预约到店服务</span><span><Icon name="orders" :size="27"/>保养记录随时可查</span></div>
          <nav class="service-categories" aria-label="养护服务分类"><button v-for="service in serviceCategories" :key="service.label" @click="chooseService(service)"><img v-if="service.image" :src="'/assets/redesign/'+service.image" alt=""/><Icon v-else :name="service.icon" :size="39" weight="duotone"/><span>{{service.label}}</span><Icon name="arrow" :size="16"/></button></nav>
          <section id="catalog-products" aria-label="保养配件商城">
          <div class="section-header"><div><h2>精选保养配件</h2><p>商品价格与保养工时费分开显示，清楚消费。</p></div><button class="text-button" @click="chooseCategory('')">查看全部 <Icon name="arrow" :size="15"/></button></div>
          <div class="category-tabs"><button v-for="c in ['','润滑油','滤清器','制动系统','电气系统','车身附件']" :key="c" :class="{selected:category===c}" :aria-pressed="category===c" @click="category=c;currentPage=1">{{c||'全部配件'}}</button><span class="catalog-count">{{total}} 件商品<span v-if="keyword"> · “{{keyword}}”的搜索结果</span></span></div>
          <div class="product-grid"><article v-for="row in catalogProducts" :key="row.product.id" class="product-card"><button class="product-visual" :aria-label="'查看'+row.product.name+'详情'" @click="active=row;modal='product-detail'"><img :src="productImage(row.product)" :alt="row.product.name" loading="lazy" @error="imageFallback"></button><div class="product-copy"><h3><button @click="active=row;modal='product-detail'">{{row.product.name}}</button></h3><div class="price-row"><strong><small>¥</small>{{money(row.product.price)}}</strong><span>工时费 ¥{{money(row.product.laborFee)}}</span></div><div class="product-bottom"><span>{{row.available?'库存 '+row.available+' 件':'暂时缺货'}}</span><button class="add-button" :disabled="!row.available||actionBusy" :aria-label="'将'+row.product.name+'加入购物车'" @click="addToCart(row.product)">加入购物车</button></div></div></article></div>
          <div v-if="!products.length&&!loading" class="empty-state"><Icon name="search" :size="40"/><h3>没有找到相关配件</h3><p>试试其他关键词或分类。</p></div>
          </section></div>
        </template>

        <template v-if="page==='cart'">
          <div class="cart-layout"><section class="panel"><div class="panel-heading"><h2>已选配件 <span class="muted">{{cart.length}} 种</span></h2><label class="check-label"><input type="checkbox" :checked="selected.length===cart.length&&cart.length>0" @change="selected=$event.target.checked?cart.filter(x=>x.product.status==='ON_SHELF').map(x=>x.cartItem.id):[]">全选</label></div><div v-for="row in cart" :key="row.cartItem.id" class="cart-row"><input v-model="selected" type="checkbox" :value="row.cartItem.id" :disabled="row.product.status!=='ON_SHELF'" :aria-label="'选择'+row.product.name"><img :src="productImage(row.product)" @error="imageFallback" alt=""><div class="cart-info"><h3>{{row.product.name}}</h3><p>单价 ¥{{money(row.product.price)}} · 工时费 ¥{{money(row.product.laborFee)}}</p><span v-if="row.product.status!=='ON_SHELF'" class="badge rejected">已下架，请移除</span></div><div class="stepper"><button aria-label="减少数量" :disabled="row.cartItem.quantity<=1||actionBusy" @click="changeQuantity(row,-1)">−</button><span>{{row.cartItem.quantity}}</span><button aria-label="增加数量" :disabled="row.cartItem.quantity>=99||actionBusy" @click="changeQuantity(row,1)">+</button></div><strong>¥{{money((Number(row.product.price)+Number(row.product.laborFee))*row.cartItem.quantity)}}</strong><button class="icon-button" aria-label="移出购物车" :disabled="actionBusy" @click="removeCart(row)"><Icon name="trash" :size="18"/></button></div><div v-if="!cart.length" class="empty-state"><Icon name="bag" :size="44"/><h3>购物车还是空的</h3><p>选几件配件，为下一次保养做好准备。</p><button class="button" @click="navigate('catalog')">去逛逛</button></div></section><aside class="panel order-summary"><h2>确认订单</h2><label>选择服务门店<select v-model="selectedStore"><option value="" disabled>请选择门店</option><option v-for="s in stores" :key="s.id" :value="s.id">{{s.name}}</option></select></label><p class="helper">审核通过后，配件将配送到这家门店。</p><dl><div><dt>商品金额</dt><dd>¥{{money(amounts.goods)}}</dd></div><div><dt>保养工时费</dt><dd>¥{{money(amounts.labor)}}</dd></div><div class="sum-total"><dt>订单总额</dt><dd>¥{{money(amounts.goods+amounts.labor)}}</dd></div></dl><button class="button full" :disabled="!selectedRows.length||!selectedStore||actionBusy" @click="checkout">提交订单 <Icon name="arrow" :size="18"/></button><p class="helper">订单通过审核后，即可预约到店时间。</p></aside></div>
        </template>

        <template v-if="['orders','order-review'].includes(page)">
          <div class="filter-row"><div class="category-tabs"><button v-for="s in ['', 'PENDING_REVIEW','READY','IN_SERVICE','COMPLETED','REJECTED']" :key="s" :class="{selected:statusFilter===s}" @click="statusFilter=s;currentPage=1;load()">{{states[s]||'全部订单'}}</button></div></div>
          <div class="order-list"><article v-for="view in orders" :key="view.order.id" class="panel order-card"><div class="order-top"><span class="order-number">{{view.order.orderNo}}</span><span class="badge" :class="view.order.status.toLowerCase()">{{states[view.order.status]}}</span><span class="order-time">{{dateTime(view.order.createdAt)}}</span></div><div class="order-body"><div class="order-items"><div v-for="item in view.items" :key="item.id"><Icon name="bag" :size="17"/><strong>{{item.nameSnapshot}}</strong><span>× {{item.quantity}}</span></div><p><Icon name="pin" :size="15"/>{{view.store.name}}</p><p v-if="view.booking"><Icon name="calendar" :size="15"/>{{view.booking.slot.serviceDate}} {{view.booking.slot.startTime}}–{{view.booking.slot.endTime}} <span v-if="view.booking.appointment.arrivedAt" class="badge approved">已到店登记</span></p><p v-if="view.order.reviewComment" class="helper">审核意见：{{view.order.reviewComment}}</p></div><div class="order-amount"><span>商品 ¥{{money(view.order.productAmount)}} + 工时 ¥{{money(view.order.laborAmount)}}</span><strong>¥{{money(view.order.totalAmount)}}</strong></div></div><div class="order-actions"><span v-if="page==='orders'&&view.voucher&&view.voucher.status==='UNUSED'" class="voucher-inline">到店凭据 <b>{{view.voucher.code}}</b></span><span v-else-if="page==='order-review'" class="helper">车主：{{view.customerName}} · {{view.customerPhone}}</span><div><button class="button secondary small" @click="showOrder(view)">订单详情</button><button v-if="page==='orders'&&view.order.status==='READY'&&!view.booking" class="button small" @click="openBook(view)">预约到店</button><button v-if="page==='orders'&&view.order.status==='COMPLETED'" class="button small secondary" @click="openReview(view)">发表评价</button><template v-if="page==='order-review'&&view.order.status==='PENDING_REVIEW'"><button class="button danger-outline small" @click="openDecision('order',view,'REJECTED')">拒绝</button><button class="button small" @click="openDecision('order',view,'APPROVED')">通过并配送</button></template></div></div></article></div>
          <div v-if="!orders.length&&!loading" class="empty-state panel"><Icon name="orders" :size="42"/><h3>暂无{{statusFilter?states[statusFilter]:''}}订单</h3><p>订单提交后会在这里显示。</p></div>
        </template>

        <template v-if="['records','service-records'].includes(page)">
          <div v-if="page==='service-records'" class="category-tabs"><button v-for="s in ['','IN_PROGRESS','COMPLETED']" :key="s" :class="{selected:statusFilter===s}" @click="statusFilter=s;currentPage=1;load()">{{states[s]||'全部服务'}}</button></div>
          <article v-for="row in records" :key="row.record.id" class="panel record-card"><div class="record-icon"><Icon name="wrench" :size="25"/></div><div class="record-main"><div class="record-heading"><h3>{{row.store.name}}</h3><span class="badge" :class="row.record.status.toLowerCase()">{{states[row.record.status]}}</span></div><p>{{row.order.orderNo}} · 预约 {{row.booking.slot.serviceDate}} {{row.booking.slot.startTime}}</p><div class="record-detail"><strong>服务项目</strong><p>{{row.record.serviceItems||'正在保养，完成后将显示服务内容。'}}</p><p v-if="row.record.note">备注：{{row.record.note}}</p></div><small>开始 {{dateTime(row.record.startedAt)}}<span v-if="row.record.completedAt"> · 完成 {{dateTime(row.record.completedAt)}}</span></small></div><button v-if="page==='service-records'&&row.record.status==='IN_PROGRESS'" class="button" @click="openComplete(row)">完成保养</button></article>
          <div v-if="!records.length&&!loading" class="empty-state panel"><Icon name="wrench" :size="42"/><h3>暂无保养记录</h3><p>到店服务开始后，保养记录会在这里显示。</p></div>
        </template>

        <template v-if="page==='stores'">
          <div class="section-header"><h2>附近的专业服务</h2><form class="search" @submit.prevent="currentPage=1;load()"><Icon name="search" :size="18"/><input v-model="keyword" aria-label="搜索门店" placeholder="搜索门店或地址"><button>搜索</button></form></div><div class="store-grid"><article v-for="s in stores" :key="s.id" class="panel store-card"><div class="store-banner"><Icon name="store" :size="48"/><span>CHETU SERVICE</span><span class="badge approved">平台审核通过</span></div><div class="store-copy"><h3>{{s.name}}</h3><p><Icon name="pin" :size="17"/>{{s.address}}</p><p><Icon name="clock" :size="17"/>{{s.businessHours}}</p><p><Icon name="user" :size="17"/>联系电话 {{s.contactPhone}}</p><div class="store-actions"><button class="button secondary small" @click="publicReviews('STORE',s)">查看评价</button><button class="button small" @click="navigate('catalog')">选购配件 <Icon name="arrow" :size="16"/></button></div></div></article></div><div v-if="!stores.length&&!loading" class="empty-state panel"><h3>暂无符合条件的门店</h3></div>
        </template>

        <template v-if="page==='overview'">
          <div v-if="user?.role==='ADMIN'" class="stats-grid"><div v-for="[key,label,icon] in [['orders','累计订单','orders'],['pendingOrders','待审核订单','clock'],['stores','服务门店','store'],['completed','已完成保养','wrench']]" :key="key" class="panel stat-card"><span>{{label}}</span><strong>{{stats[key]||0}}</strong><div class="stat-icon"><Icon :name="icon" :size="24"/></div><small>{{key==='pendingOrders'?'及时处理，为车主安排服务':'平台业务数据'}}</small></div></div>
          <template v-else><section class="store-welcome"><div><span class="eyebrow">SERVICE CENTER</span><h2>{{profile?.name}}</h2><p>{{profile?.address}}</p><p>{{profile?.businessHours}}</p></div><Icon name="store" :size="95"/></section><div class="stats-grid two"><div class="panel stat-card"><span>门店预约</span><strong>{{stats.appointments||0}}</strong><small>本店累计预约数量</small></div><div class="panel stat-card"><span>正在服务</span><strong>{{stats.inProgress||0}}</strong><small>待完成并留存记录</small></div></div></template>
          <div class="overview-grid"><section class="panel"><div class="panel-heading"><h2>{{user?.role==='ADMIN'?'待审核订单':'近期预约'}}</h2><button class="text-button" @click="navigate(user.role==='ADMIN'?'order-review':'appointments')">查看全部 <Icon name="arrow" :size="16"/></button></div><template v-if="user?.role==='ADMIN'"><div v-for="view in orders" :key="view.order.id" class="overview-row"><div><strong>{{view.customerName}}</strong><p>{{view.store.name}}</p></div><span>¥{{money(view.order.totalAmount)}}</span><span class="badge pending">待审核</span></div><p v-if="!orders.length" class="inline-empty">当前没有待审核订单</p></template><template v-else><div v-for="a in appointments" :key="a.appointment.id" class="overview-row"><div><strong>{{a.orderView.customerName}}</strong><p>{{a.slot.serviceDate}} {{a.slot.startTime}}</p></div><span class="badge" :class="a.appointment.status.toLowerCase()">{{a.appointment.arrivedAt&&a.appointment.status==='BOOKED'?'已登记':states[a.appointment.status]}}</span></div><p v-if="!appointments.length" class="inline-empty">暂无预约安排</p></template></section><section class="panel"><div class="panel-heading"><h2>{{user?.role==='ADMIN'?'加盟店申请':'快捷操作'}}</h2></div><template v-if="user?.role==='ADMIN'"><div v-for="s in applications" :key="s.id" class="overview-row"><div><strong>{{s.name}}</strong><p>{{s.contactName}} · {{s.contactPhone}}</p></div><button class="text-button" @click="navigate('store-review')">去审核</button></div><p v-if="!applications.length" class="inline-empty">当前没有待审核申请</p></template><template v-else><button class="quick-action" @click="navigate('checkin')"><Icon name="qr" :size="28"/><div><strong>到店凭据核验</strong><p>查看二维码，核验并开始服务</p></div><Icon name="arrow"/></button><button class="quick-action" @click="navigate('service-records')"><Icon name="wrench" :size="28"/><div><strong>完成保养服务</strong><p>登记项目、备注与完成时间</p></div><Icon name="arrow"/></button></template></section></div>
        </template>

        <template v-if="page==='products'">
          <div class="section-header"><p class="helper">库存 = 现存数量；预占库存属于待审核订单，不能被重复出售。</p><form class="search" @submit.prevent="currentPage=1;load()"><Icon name="search" :size="18"/><input v-model="keyword" placeholder="商品名称 / SKU" aria-label="查找商品"><button>搜索</button></form></div><div class="panel table-wrap"><table><thead><tr><th>商品信息</th><th>售价 / 工时费</th><th>库存 / 预占</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in products" :key="row.product.id"><td><div class="table-product"><img :src="productImage(row.product)" @error="imageFallback" alt=""><div><strong>{{row.product.name}}</strong><small>{{row.product.sku}} · {{row.product.category}}</small></div></div></td><td><strong>¥{{money(row.product.price)}}</strong><small>工时 ¥{{money(row.product.laborFee)}}</small></td><td><strong>{{row.stock}}</strong><small>预占 {{row.reserved}} · 可售 {{row.available}}</small></td><td><span class="badge" :class="row.product.status.toLowerCase()">{{states[row.product.status]}}</span></td><td><div class="table-actions"><button class="text-button" @click="editProduct(row)">编辑</button><button class="text-button danger" @click="askDelete(row)">删除</button></div></td></tr></tbody></table><div v-if="!products.length&&!loading" class="inline-empty">暂无商品</div></div>
        </template>

        <template v-if="page==='store-review'">
          <div class="category-tabs"><button v-for="s in ['','PENDING','APPROVED','REJECTED']" :key="s" :class="{selected:statusFilter===s}" @click="statusFilter=s;currentPage=1;load()">{{states[s]||'全部申请'}}</button></div><article v-for="s in applications" :key="s.id" class="panel application-card"><div class="application-icon"><Icon name="store" :size="32"/></div><div class="application-copy"><h3>{{s.name}} <span class="badge" :class="s.auditStatus.toLowerCase()">{{states[s.auditStatus]}}</span></h3><p>{{s.address}}</p><p>{{s.contactName}} · {{s.contactPhone}} · {{s.businessHours}}</p><small>申请时间 {{dateTime(s.createdAt)}}<span v-if="s.auditComment"> · 审核意见：{{s.auditComment}}</span></small></div><div v-if="s.auditStatus==='PENDING'" class="table-actions"><button class="button danger-outline small" @click="openDecision('store',s,'REJECTED')">拒绝</button><button class="button small" @click="openDecision('store',s,'APPROVED')">通过申请</button></div></article><div v-if="!applications.length&&!loading" class="empty-state panel"><Icon name="store" :size="42"/><h3>暂无加盟申请</h3></div>
        </template>

        <template v-if="page==='checkin'">
          <div class="checkin-grid"><section class="panel verify-panel"><div class="circle-icon"><Icon name="qr" :size="30"/></div><h2>核验到店凭据</h2><p>输入车主订单中的十位数字凭据</p><form @submit.prevent="verify"><label>数字凭据<input v-model="voucherCode" class="code-input" placeholder="0000000000" inputmode="numeric" pattern="[0-9]{10}" maxlength="10" required></label><button class="button full" :disabled="actionBusy">核验凭据 <Icon name="arrow" :size="18"/></button></form><div v-if="verified" class="verified-card"><span class="badge approved"><Icon name="check" :size="14"/> 核验通过</span><h3>{{verified.orderView.customerName}} · {{verified.orderView.customerPhone}}</h3><p v-for="item in verified.orderView.items" :key="item.id">{{item.nameSnapshot}} × {{item.quantity}}</p><p>预约：{{verified.orderView.booking.slot.serviceDate}} {{verified.orderView.booking.slot.startTime}}–{{verified.orderView.booking.slot.endTime}}</p><button class="button full" :disabled="actionBusy" @click="startService">核销凭据并开始服务</button></div><p class="helper">仅能核验本店订单，且需在预约当天操作。</p></section><section class="panel qr-panel"><h2>门店到店二维码</h2><p>车主用手机扫码后，登录并输入凭据完成登记。</p><img v-if="qrImage" :src="qrImage" alt="本店到店登记二维码" class="qr-image"><strong>{{profile?.name}}</strong><label>手机可访问的前端地址<input v-model="qrBase" placeholder="http://电脑局域网IP:5173"></label><button class="button secondary full" @click="makeQr">更新二维码</button><p class="helper">手机与电脑需连接同一网络。请将 localhost 换成电脑局域网 IP，或使用部署后的网址。</p><a class="text-button" :href="'?checkin='+profile?.id" target="_blank" rel="noopener">打开登记页面 <Icon name="arrow" :size="15"/></a></section></div>
        </template>

        <template v-if="page==='arrival'"><section class="panel arrival-panel"><div class="circle-icon"><Icon name="pin" :size="30"/></div><span class="eyebrow">WELCOME TO CHETU</span><h2>{{stores.find(s=>s.id===scanStore)?.name||'到店登记'}}</h2><p>欢迎到店。请填写订单中的十位数字凭据。</p><div v-if="!user" class="arrival-login"><p>请先登录下单时使用的车主账号</p><button class="button full" @click="openAuth()">登录车主账号</button></div><form v-else-if="user.role==='CUSTOMER'" @submit.prevent="arrive"><label>订单数字凭据<input v-model="arrivalCode" class="code-input" inputmode="numeric" pattern="[0-9]{10}" maxlength="10" placeholder="0000000000" required></label><button class="button full" :disabled="actionBusy">确认到店登记</button><p class="helper">登记后由门店人员核验并开始服务。</p></form><p v-else>请退出当前账号，使用下单车主账号登录。</p></section></template>

        <template v-if="page==='appointments'">
          <div class="filter-row"><label class="inline-label">预约日期<input v-model="appointmentDate" type="date" @change="currentPage=1;load()"></label><button class="text-button" @click="appointmentDate='';load()">全部日期</button></div><div class="panel table-wrap"><table><thead><tr><th>车主</th><th>预约时间</th><th>配件</th><th>到店登记</th><th>状态</th></tr></thead><tbody><tr v-for="a in appointments" :key="a.appointment.id"><td><strong>{{a.orderView.customerName}}</strong><small>{{a.orderView.customerPhone}}</small></td><td>{{a.slot.serviceDate}}<small>{{a.slot.startTime}}–{{a.slot.endTime}}</small></td><td><p v-for="i in a.orderView.items" :key="i.id" class="table-item">{{i.nameSnapshot}} × {{i.quantity}}</p></td><td>{{a.appointment.arrivedAt?dateTime(a.appointment.arrivedAt):'尚未登记'}}</td><td><span class="badge" :class="a.appointment.status.toLowerCase()">{{states[a.appointment.status]}}</span></td></tr></tbody></table><div v-if="!appointments.length&&!loading" class="inline-empty">该日期暂无预约</div></div>
        </template>

        <template v-if="page==='slots'"><p class="helper slot-intro">修改已有时段时可调整容量和开放状态。日期与时间固定；新增时段请点击右上角。</p><div class="panel table-wrap"><table><thead><tr><th>服务日期</th><th>服务时段</th><th>预约 / 容量</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="s in slots" :key="s.id"><td>{{s.serviceDate}}</td><td>{{s.startTime}}–{{s.endTime}}</td><td><strong>{{s.reservedCount}}</strong> / {{s.capacity}}</td><td><span class="badge" :class="s.status.toLowerCase()">{{states[s.status]}}</span></td><td><button class="text-button" @click="editSlot(s)">调整</button></td></tr></tbody></table><div v-if="!slots.length&&!loading" class="inline-empty">暂无服务时段</div></div></template>

        <template v-if="page==='audit'"><div class="panel table-wrap"><table><thead><tr><th>时间</th><th>操作人ID</th><th>操作类型</th><th>对象</th><th>详情</th></tr></thead><tbody><tr v-for="a in audits" :key="a.id"><td>{{dateTime(a.createdAt)}}</td><td>{{a.actorId}}</td><td>{{a.action}}</td><td>{{a.targetType}} #{{a.targetId}}</td><td class="audit-detail">{{a.detail}}<small>追踪 {{a.traceId}}</small></td></tr></tbody></table><div v-if="!audits.length&&!loading" class="inline-empty">暂无操作日志</div></div></template>

        <div v-if="total>pageSize" class="pagination"><span>共 {{total}} 条</span><button :disabled="currentPage<=1||loading" @click="paginate(-1)">上一页</button><strong>{{currentPage}} / {{Math.ceil(total/pageSize)}}</strong><button :disabled="currentPage>=Math.ceil(total/pageSize)||loading" @click="paginate(1)">下一页</button></div>
        <footer class="page-footer" :class="{'catalog-footer':page==='catalog'}"><div v-if="page==='catalog'" class="care-process content-width"><button @click="chooseCategory('')"><Icon name="bag" :size="23"/>选购配件</button><Icon name="arrow" :size="15"/><button @click="navigate('stores')"><Icon name="pin" :size="23"/>选择门店</button><Icon name="arrow" :size="15"/><button @click="navigate('orders')"><Icon name="truck" :size="23"/>审核配送</button><Icon name="arrow" :size="15"/><button @click="navigate('orders')"><Icon name="calendar" :size="23"/>预约到店</button><span>车途养护 · 用心养护，安心出发。</span></div><template v-else><span>车途养护 CHETU AUTOCARE</span><span>用心养护，安心出发。</span></template></footer>
      </main>
    </div>

    <div v-if="toast" class="toast" role="status"><Icon name="check" :size="18"/>{{toast}}</div>
    <div v-if="modal" class="modal-overlay" @click.self="!actionBusy&&(modal='')" @keydown.esc="!actionBusy&&(modal='')"><section class="modal" :class="{wide:['product','order'].includes(modal)}" role="dialog" aria-modal="true" :aria-label="modal==='auth'?'账号登录与注册':'操作窗口'"><button class="modal-close icon-button" aria-label="关闭窗口" :disabled="actionBusy" @click="modal=''"><Icon name="close"/></button>
      <template v-if="modal==='auth'"><div class="modal-brand"><Icon name="car" :size="28"/>车途养护</div><h2>{{authMode==='login'?'欢迎回来':authMode==='customer'?'开启你的养车之旅':'成为车途加盟伙伴'}}</h2><p class="modal-description">{{authMode==='store'?'提交申请，通过审核后即可提供门店服务。':'一站完成配件选购与专业保养。'}}</p><div class="auth-tabs"><button v-for="[id,label] in [['login','登录'],['customer','车主注册'],['store','加盟注册']]" :key="id" :class="{selected:authMode===id}" @click="authMode=id;authError=''">{{label}}</button></div><form @submit.prevent="authenticate"><label>登录名{{authMode==='login'?' / 手机号':''}}<input v-model="form.loginName" :autocomplete="authMode==='login'?'username':'off'" placeholder="请输入账号" maxlength="50" required :pattern="authMode==='login'?undefined:'[A-Za-z0-9_]{3,50}'"></label><label v-if="authMode!=='login'">手机号<input v-model="form.phone" inputmode="tel" pattern="1[3-9][0-9]{9}" maxlength="11" placeholder="11位手机号" required></label><label>密码<input v-model="form.password" type="password" :autocomplete="authMode==='login'?'current-password':'new-password'" :minlength="authMode==='login'?1:8" maxlength="72" placeholder="请输入密码" required></label><template v-if="authMode==='store'"><div class="form-grid"><label>门店名称<input v-model="form.name" maxlength="100" required></label><label>联系人<input v-model="form.contactName" maxlength="50" required></label><label>联系人手机<input v-model="form.contactPhone" pattern="1[3-9][0-9]{9}" maxlength="11" required></label><label>营业时间<input v-model="form.businessHours" maxlength="100" required></label></div><label>门店地址<input v-model="form.address" maxlength="255" required></label></template><p v-if="authError" class="form-error" role="alert">{{authError}}</p><button class="button full" :disabled="actionBusy">{{actionBusy?'处理中…':authMode==='login'?'登录账号':authMode==='customer'?'注册车主账号':'提交加盟申请'}}</button></form><div v-if="authMode==='login'" class="demo-accounts"><span>本地演示账号</span><button @click="demoLogin('CUSTOMER')">车主</button><button @click="demoLogin('STORE')">门店</button><button @click="demoLogin('ADMIN')">管理员</button></div></template>
      <template v-if="modal==='product-detail'"><img class="detail-product-image" :src="productImage(active.product)" @error="imageFallback" :alt="active.product.name"><span class="eyebrow">{{active.product.category}} · {{active.product.sku}}</span><h2>{{active.product.name}}</h2><p class="modal-description">{{active.product.description}}</p><div class="detail-prices"><strong>商品 ¥{{money(active.product.price)}}</strong><span>单位工时费 ¥{{money(active.product.laborFee)}}</span></div><p class="helper">可用库存 {{active.available}} 件。工时费按购买数量计算。</p><div class="modal-actions"><button class="button secondary" @click="publicReviews('PRODUCT',active.product)">查看评价</button><button class="button" :disabled="!active.available||actionBusy" @click="addToCart(active.product)">加入购物车</button></div></template>
      <template v-if="modal==='checkout'"><h2>确认提交订单</h2><p class="modal-description">{{selectedRows.length}} 种配件将配送到 {{stores.find(s=>s.id===Number(selectedStore))?.name}}。</p><dl class="confirmation-dl"><div><dt>商品金额</dt><dd>¥{{money(amounts.goods)}}</dd></div><div><dt>保养工时费</dt><dd>¥{{money(amounts.labor)}}</dd></div><div><dt>总计</dt><dd>¥{{money(amounts.goods+amounts.labor)}}</dd></div></dl><p class="helper">最终金额以提交时的商品价格为准。下单后等待平台审核，通过后预约到店。</p><button class="button full" :disabled="actionBusy" @click="submitOrder">确认下单</button></template>
      <template v-if="modal==='booking'"><h2>预约到店保养</h2><p class="modal-description">{{active.store.name}}</p><label>服务日期<select v-model="bookingDate"><option v-for="d in bookDates" :key="d" :value="d">{{d}}</option></select></label><div class="slot-options"><button v-for="s in dateSlots" :key="s.id" :disabled="s.reservedCount>=s.capacity||(s.serviceDate===today&&s.endTime<=new Intl.DateTimeFormat('en-GB',{hour:'2-digit',minute:'2-digit',timeZone:'Asia/Shanghai'}).format(new Date()))" :class="{selected:selectedSlot===s.id}" @click="selectedSlot=s.id"><strong>{{s.startTime}}–{{s.endTime}}</strong><span>剩余 {{s.capacity-s.reservedCount}} 个名额</span></button></div><p v-if="!bookingSlots.length" class="helper">门店暂无开放时段，请稍后再试。</p><button class="button full" :disabled="!selectedSlot||actionBusy" @click="book">确认预约</button></template>
      <template v-if="modal==='order'"><h2>订单详情</h2><p class="order-number">{{active.order.orderNo}}</p><span class="badge" :class="active.order.status.toLowerCase()">{{states[active.order.status]}}</span><div class="detail-section"><h3>配件清单</h3><div v-for="i in active.items" :key="i.id" class="detail-item"><strong>{{i.nameSnapshot}} × {{i.quantity}}</strong><span>商品 ¥{{money(i.unitPriceSnapshot)}} / 工时 ¥{{money(i.unitLaborFeeSnapshot)}}<br>小计 ¥{{money(i.lineAmount)}}</span></div><dl class="confirmation-dl"><div><dt>商品合计</dt><dd>¥{{money(active.order.productAmount)}}</dd></div><div><dt>工时费合计</dt><dd>¥{{money(active.order.laborAmount)}}</dd></div><div><dt>订单总额</dt><dd>¥{{money(active.order.totalAmount)}}</dd></div></dl></div><div class="detail-section"><h3>服务门店</h3><p>{{active.store.name}} · {{active.store.address}}</p><p v-if="active.booking">预约 {{active.booking.slot.serviceDate}} {{active.booking.slot.startTime}}–{{active.booking.slot.endTime}}</p><p v-else class="helper">{{active.order.status==='READY'?'尚未预约，请选择到店时段。':'审核通过后即可预约。'}}</p></div><div v-if="active.voucher" class="voucher-box"><span>十位到店凭据 · {{states[active.voucher.status]}}</span><strong>{{active.voucher.code}}</strong><button class="text-button" @click="copy(active.voucher.code)">复制凭据</button></div><p v-if="active.order.reviewComment" class="helper">审核意见：{{active.order.reviewComment}}</p><button v-if="page==='orders'&&active.order.status==='READY'&&!active.booking" class="button full" @click="openBook(active)">预约到店</button></template>
      <template v-if="modal==='decision'"><h2>{{form.decision==='APPROVED'?'通过':'拒绝'}}{{form.type==='store'?'加盟申请':'订单审核'}}</h2><p class="modal-description">{{form.type==='store'?active.name:active.order.orderNo}}</p><p class="helper">{{form.type==='order'?(form.decision==='APPROVED'?'通过后将扣减现存和预占库存，配件即时送达门店，并签发唯一数字凭据。':'拒绝后会释放预占库存。'):form.decision==='APPROVED'?'通过后门店账号生效，并开放未来14天默认预约时段。':'拒绝后门店账号不可登录，请说明原因。'}}</p><form @submit.prevent="decide"><label>审核意见{{form.decision==='REJECTED'?'（必填）':'（选填）'}}<textarea v-model="form.comment" :required="form.decision==='REJECTED'" maxlength="500" rows="3" placeholder="填写审核意见"></textarea></label><button class="button full" :class="{'danger-solid':form.decision==='REJECTED'}" :disabled="actionBusy">确认{{form.decision==='APPROVED'?'通过':'拒绝'}}</button></form></template>
      <template v-if="modal==='product'"><h2>{{active?'编辑商品':'新增保养配件'}}</h2><form @submit.prevent="saveProduct"><div class="form-grid"><label>商品名称<input v-model="form.name" maxlength="120" required></label><label>商品编码 SKU<input v-model="form.sku" maxlength="50" required></label><label>分类<select v-model="form.category"><option v-for="c in ['润滑油','滤清器','制动系统','电气系统','车身附件']" :key="c">{{c}}</option></select></label><label>上架状态<select v-model="form.status"><option value="ON_SHELF">已上架</option><option value="OFF_SHELF">已下架</option></select></label><label>商品售价（元）<input v-model.number="form.price" type="number" min="0" max="999999.99" step="0.01" required></label><label>单位工时费（元）<input v-model.number="form.laborFee" type="number" min="0" max="999999.99" step="0.01" required></label><label>现存库存<input v-model.number="form.stock" type="number" :min="active?.reserved||0" max="1000000" step="1" required></label><label>封面图片地址<input v-model="form.imageUrl" placeholder="/assets/oil.svg 或 https://…" maxlength="500"></label></div><label>商品描述<textarea v-model="form.description" maxlength="2000" rows="3"></textarea></label><p v-if="active" class="helper">当前预占 {{active.reserved}} 件，库存不能小于预占数量。</p><button class="button full" :disabled="actionBusy">保存商品</button></form></template>
      <template v-if="modal==='delete'"><h2>删除这个商品？</h2><p class="modal-description">{{active.product.name}}</p><p class="helper">商品将从目录移除。已有订单和保养记录将保留。</p><button class="button danger-solid full" :disabled="actionBusy" @click="deleteProduct">确认删除</button></template>
      <template v-if="modal==='complete'"><h2>完成保养服务</h2><p class="modal-description">{{active.order.orderNo}}</p><form @submit.prevent="complete"><label>已完成服务项目<textarea v-model="form.serviceItems" rows="4" maxlength="2000" required placeholder="例如：更换发动机机油、机油滤清器；检查制动系统"></textarea></label><label>保养备注（选填）<textarea v-model="form.note" rows="3" maxlength="2000" placeholder="检查结果、下次保养建议等"></textarea></label><button class="button full" :disabled="actionBusy">完成并留存记录</button></form></template>
      <template v-if="modal==='slot'"><h2>{{active?'调整预约时段':'新增预约时段'}}</h2><form @submit.prevent="saveSlot"><label>服务日期<input v-model="form.serviceDate" type="date" :min="today" :disabled="!!active" required></label><div class="form-grid"><label>开始时间<input v-model="form.startTime" type="time" :disabled="!!active" required></label><label>结束时间<input v-model="form.endTime" type="time" :disabled="!!active" required></label><label>容量<input v-model.number="form.capacity" type="number" :min="Math.max(1,active?.reservedCount||0)" max="100" step="1" required></label><label>开放状态<select v-model="form.status"><option value="OPEN">开放</option><option value="CLOSED">关闭</option></select></label></div><p v-if="active" class="helper">已有 {{active.reservedCount}} 个预约。关闭时段不取消已有预约。</p><button class="button full" :disabled="actionBusy">保存时段</button></form></template>
      <template v-if="modal==='review'"><h2>分享保养体验</h2><form @submit.prevent="submitReview"><label>评价对象<select :value="form.targetType+':'+form.targetId" @change="reviewTarget"><option :value="'STORE:'+active.store.id">门店：{{active.store.name}}</option><option v-for="i in active.items" :key="i.id" :value="'PRODUCT:'+i.productId">商品：{{i.nameSnapshot}}</option></select></label><label>评分<select v-model.number="form.rating"><option v-for="n in [5,4,3,2,1]" :key="n" :value="n">{{'★'.repeat(n)}} {{n}}分</option></select></label><label>评价内容<textarea v-model="form.content" rows="4" maxlength="1000" required placeholder="说说配件或门店服务的体验"></textarea></label><button class="button full" :disabled="actionBusy">发布评价</button></form></template>
      <template v-if="modal==='reviews'"><h2>车主评价</h2><p class="modal-description">{{active.name}}</p><div v-for="r in reviewRows" :key="r.id" class="review-row"><div><strong>{{r.author}}</strong><span class="stars">{{'★'.repeat(r.rating)}}{{'☆'.repeat(5-r.rating)}}</span></div><p>{{r.content}}</p><small>{{dateTime(r.createdAt)}}</small></div><div v-if="!reviewRows.length" class="inline-empty">暂无评价，完成保养后欢迎分享体验。</div></template>
    </section></div>
  </div>
</template>
