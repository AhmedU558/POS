async function run() {
  const testLogin = async (username, password) => {
    const res = await fetch("http://localhost:8080/api/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });
    console.log(username, password, res.status);
    const body = await res.text();
    console.log(body);
  };
  
  await testLogin("ada.lovelace", "ChangeMeNow-1");
  await new Promise(r => setTimeout(r, 100));
  await testLogin("demo.admin", "demo");
}
run();
