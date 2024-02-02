export class PluginsElement extends HTMLElement {
  connectedCallback() {
    console.log('init hola', state);
  }
}

export function initialize() {
  customElements.define('penpot-plugins', PluginsElement);

  const channel = new BroadcastChannel("penpot:plugins");

  channel.addEventListener("message", (event) => {
    const eventData = event.data;
    console.log("PluginsRuntime | received:", eventData);

    if (eventData === "initialized") {
      console.log("PluginsRuntime | sending ping");
      channel.postMessage("ping");
    }
  });

  console.log("PluginsRuntime | initialized");
};
