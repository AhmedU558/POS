const fs = require('fs');

let code1 = fs.readFileSync('src/features/auth/AuthContext.test.tsx', 'utf8');
code1 = code1.replace(/expect\(screen\.getByTestId\('auth-status'\)\)\.toHaveTextContent\('Unauth'\);/g, 'expect(screen.getByTestId(\'auth-status\').textContent).toBe(\'Unauth\');');
code1 = code1.replace(/expect\(screen\.getByTestId\('auth-status'\)\)\.toHaveTextContent\('Auth'\);/g, 'expect(screen.getByTestId(\'auth-status\').textContent).toBe(\'Auth\');');
code1 = code1.replace(/expect\(screen\.getByTestId\('pwd-status'\)\)\.toHaveTextContent\('PwdReq'\);/g, 'expect(screen.getByTestId(\'pwd-status\').textContent).toBe(\'PwdReq\');');
code1 = code1.replace(/expect\(screen\.getByTestId\('user-name'\)\)\.toHaveTextContent\('testuser'\);/g, 'expect(screen.getByTestId(\'user-name\').textContent).toBe(\'testuser\');');
fs.writeFileSync('src/features/auth/AuthContext.test.tsx', code1);