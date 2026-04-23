import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import { QIANXUN_ROOT_ELEMENT_ID } from "./lib/qianxunConstants";
import "./styles.css";

const mountEl =
  document.getElementById(QIANXUN_ROOT_ELEMENT_ID) ??
  document.getElementById("root");

if (!mountEl) {
  throw new Error("未找到前端挂载节点（qianxun-root / root）");
}

ReactDOM.createRoot(mountEl).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
