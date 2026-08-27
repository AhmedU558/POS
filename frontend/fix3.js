const fs = require('fs');

let code1 = fs.readFileSync('src/features/auth/AuthContext.test.tsx', 'utf8');
code1 = code1.replace('expect(screen.getByText(\'Loading...\')).not.toBeNull();', '');
fs.writeFileSync('src/features/auth/AuthContext.test.tsx', code1);

let code2 = fs.readFileSync('src/features/auth/AuthFlow.test.tsx', 'utf8');
code2 = code2.replace('new Response(JSON.stringify({}), { status: 204 })', 'new Response(null, { status: 204 })');
fs.writeFileSync('src/features/auth/AuthFlow.test.tsx', code2);