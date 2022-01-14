export interface EspProvisioningPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
