import api from "goog:app.api";

console.log(api);

class PluginsElement extends HTMLElement {
  connectedCallback() {
    const state = api.getState()

    console.log('init hola', state);
  }
}

// This does not work with hot reload
customElements.define('penpot-plugins', PluginsElement);
