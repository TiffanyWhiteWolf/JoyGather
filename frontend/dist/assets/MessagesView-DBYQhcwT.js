import{c as u,d as b,a as l,f as e,b as c,u as i,S as I,F as m,x as k,P as p,t,h as r,j as M,v as w,Q as z,i as V,g as _,r as x,m as S,p as o,n as f}from"./index-Dp7Cc0zr.js";import{M as C}from"./map-pin-Cd64VN05.js";import{_ as j}from"./_plugin-vue_export-helper-DlAUqK2U.js";/**
 * @license lucide-vue-next v0.468.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const g=u("EllipsisIcon",[["circle",{cx:"12",cy:"12",r:"1",key:"41hilf"}],["circle",{cx:"19",cy:"12",r:"1",key:"1wjl8i"}],["circle",{cx:"5",cy:"12",r:"1",key:"1pcz8c"}]]);/**
 * @license lucide-vue-next v0.468.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const E=u("FileIcon",[["path",{d:"M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z",key:"1rqfz7"}],["path",{d:"M14 2v4a2 2 0 0 0 2 2h4",key:"tnqrlb"}]]);/**
 * @license lucide-vue-next v0.468.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const F=u("ImageIcon",[["rect",{width:"18",height:"18",x:"3",y:"3",rx:"2",ry:"2",key:"1m3agn"}],["circle",{cx:"9",cy:"9",r:"2",key:"af1f0g"}],["path",{d:"m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",key:"1xmnt7"}]]);/**
 * @license lucide-vue-next v0.468.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const N=u("SendIcon",[["path",{d:"M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",key:"1ffxy3"}],["path",{d:"m21.854 2.147-10.94 10.939",key:"12cjpa"}]]);/**
 * @license lucide-vue-next v0.468.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const B=u("SmileIcon",[["circle",{cx:"12",cy:"12",r:"10",key:"1mglay"}],["path",{d:"M8 14s1.5 2 4 2 4-2 4-2",key:"1y1vjs"}],["line",{x1:"9",x2:"9.01",y1:"9",y2:"9",key:"yxxnd0"}],["line",{x1:"15",x2:"15.01",y1:"9",y2:"9",key:"1p4y9e"}]]),D={class:"container messages-page"},K={class:"chat-shell"},L={class:"conversation-list"},T={class:"chat-title"},q={class:"chat-search"},P={class:"conversations"},$=["onClick"],H={class:"avatar"},Q=["src"],U={key:0},Z={class:"conversation-copy"},A={key:0},G={class:"chat-main"},J=["src"],O={class:"messages"},R={key:0,class:"announcement"},W={key:0,src:"https://i.pravatar.cc/80?img=45"},X={key:1,class:"no-messages"},Y={class:"composer"},ee={class:"compose-tools"},se={class:"compose-input"},te=["onKeydown"],ne=["disabled"],ae={class:"chat-info"},le=["src"],oe=b({__name:"MessagesView",setup(ie){const v=x(p[0].id),d=x(""),n=S(()=>p.find(y=>y.id===v.value));function h(){d.value.trim()&&(n.value.messages.push({id:`m${Date.now()}`,senderId:"u-001",content:d.value,time:"刚刚",mine:!0,read:!1}),d.value="")}return(y,a)=>(o(),l("div",D,[e("div",K,[e("aside",L,[e("div",T,[a[1]||(a[1]=e("h1",null,"消息",-1)),e("button",null,[c(i(g))])]),e("div",q,[c(i(I),{size:16}),a[2]||(a[2]=e("input",{placeholder:"搜索联系人或小队"},null,-1))]),e("div",P,[(o(!0),l(m,null,k(i(p),s=>(o(),l("button",{key:s.id,class:f({active:s.id===v.value}),onClick:ce=>v.value=s.id},[e("span",H,[e("img",{src:s.avatar},null,8,Q),s.online?(o(),l("i",U)):r("",!0)]),e("span",Z,[e("b",null,[_(t(s.name),1),e("small",null,t(s.lastTime),1)]),e("em",null,t(s.lastMessage),1)]),s.unread?(o(),l("strong",A,t(s.unread),1)):r("",!0)],10,$))),128))])]),e("main",G,[e("header",null,[e("div",null,[e("img",{src:n.value.avatar},null,8,J),e("span",null,[e("b",null,t(n.value.name),1),e("small",null,t(n.value.type==="小队"?"186 位成员 · 28 人在线":"当前在线"),1)])]),e("button",null,[c(i(g))])]),e("div",O,[a[3]||(a[3]=e("div",{class:"date-divider"},"今天 10:30",-1)),n.value.type==="小队"?(o(),l("div",R,"📣 群公告：周六 18:20 桥西游客中心集合，请穿舒服的鞋。")):r("",!0),(o(!0),l(m,null,k(n.value.messages,s=>(o(),l("div",{key:s.id,class:f(["message",{mine:s.mine}])},[s.mine?r("",!0):(o(),l("img",W)),e("div",null,[e("p",null,t(s.content),1),e("span",null,t(s.time)+" "+t(s.mine?s.read?"已读":"未读":""),1)])],2))),128)),n.value.messages.length?r("",!0):(o(),l("div",X,"还没有消息，发个招呼吧 👋"))]),e("div",Y,[e("div",ee,[e("button",null,[c(i(B),{size:19})]),e("button",null,[c(i(F),{size:19})]),e("button",null,[c(i(E),{size:19})]),e("button",null,[c(i(C),{size:19})])]),e("div",se,[M(e("textarea",{"onUpdate:modelValue":a[0]||(a[0]=s=>d.value=s),placeholder:"输入消息，按 Enter 发送",onKeydown:z(V(h,["prevent"]),["enter"])},null,40,te),[[w,d.value]]),e("button",{disabled:!d.value.trim(),onClick:h},[c(i(N),{size:18})],8,ne)])])]),e("aside",ae,[e("img",{src:n.value.avatar},null,8,le),e("h3",null,t(n.value.name),1),e("p",null,t(n.value.type==="小队"?"不赶路的城市散步，欢迎每一个慢热的人。":"在落日散步活动中认识"),1),a[4]||(a[4]=e("div",null,[e("span",null,[_("消息免打扰 "),e("input",{type:"checkbox"})]),e("span",null,[_("置顶会话 "),e("input",{type:"checkbox",checked:""})])],-1)),e("button",null,"查看"+t(n.value.type==="小队"?"小队主页":"个人主页"),1)])])]))}}),ve=j(oe,[["__scopeId","data-v-0564e879"]]);export{ve as default};
