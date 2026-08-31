import { redirect } from 'next/navigation';

/**
 * Sales history is now the Sales screen itself.
 *
 * Kept as a redirect so existing links do not 404, and because "Sales" and "Sales history" as two
 * separate destinations was one of the duplications this pass set out to remove.
 */
export default function SalesHistoryRedirectPage() {
  redirect('/sales');
}
