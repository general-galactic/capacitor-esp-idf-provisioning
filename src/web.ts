import { WebPlugin } from '@capacitor/core';

import type { EspProvisioningPlugin } from './definitions';

export class EspProvisioningWeb
  extends WebPlugin
  implements EspProvisioningPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
