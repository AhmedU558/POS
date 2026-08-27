const fs = require('fs');
let code1 = fs.readFileSync('src/features/auth/AuthContext.test.tsx', 'utf8');
code1 = code1.replace('import "@testing-library/jest-dom";\n', '');
code1 = code1.replace('import { render, screen, waitFor, act } from \'@testing-library/react\';', 'import { render, screen, waitFor, act, cleanup } from \'@testing-library/react\';');
code1 = code1.replace('vi.restoreAllMocks();', 'vi.restoreAllMocks(); cleanup();');
code1 = code1.replace(/\.toBeInTheDocument\(\)/g, '.not.toBeNull()');
fs.writeFileSync('src/features/auth/AuthContext.test.tsx', code1);

let code2 = fs.readFileSync('src/features/auth/AuthFlow.test.tsx', 'utf8');
code2 = code2.replace('import "@testing-library/jest-dom";\n', '');
code2 = code2.replace('import { render, screen, waitFor, fireEvent } from \'@testing-library/react\';', 'import { render, screen, waitFor, fireEvent, cleanup } from \'@testing-library/react\';');
code2 = code2.replace('vi.restoreAllMocks();', 'vi.restoreAllMocks(); cleanup();');
code2 = code2.replace(/\.not\.toBeInTheDocument\(\)/g, '.toBeNull()');
code2 = code2.replace(/\.toBeInTheDocument\(\)/g, '.not.toBeNull()');
fs.writeFileSync('src/features/auth/AuthFlow.test.tsx', code2);