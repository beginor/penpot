export class PluginsElement extends HTMLElement {
  connectedCallback() {
    console.log('PluginsElement.connectedCallback');
  }
}

customElements.define('penpot-plugins', PluginsElement);

// Alternative to message passing
export function initialize(api) {
  console.log("PluginsRuntime:initialize", api)

  setTimeout(() => {
    const file = api.getCurrentFile();
    console.log("PluginsRuntime:initialize", file);
  }, 5000);

};
