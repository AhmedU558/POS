import { redirect } from 'next/navigation';

/**
 * Categories, brands and units are managed together under Products.
 *
 * Kept as a redirect rather than deleted so existing links and bookmarks still land somewhere
 * useful instead of on a 404.
 */
export default function UnitsRedirectPage() {
  redirect('/products/reference');
}
