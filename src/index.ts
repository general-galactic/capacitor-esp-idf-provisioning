import { registerPlugin } from '@capacitor/core';

import type { EspProvisioningPlugin } from './definitions';

const EspProvisioning = registerPlugin<EspProvisioningPlugin>(
  'EspProvisioning',
  {
    web: () => import('./web').then(m => new m.EspProvisioningWeb()),
  },
);

export * from './definitions';
export { EspProvisioning };
