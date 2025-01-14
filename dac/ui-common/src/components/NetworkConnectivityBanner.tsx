/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useNetworkAvailable } from "../utilities/useNetworkAvailable";
import { SectionMessage } from "dremio-ui-lib/components";
import { getIntlContext } from "../contexts/IntlContext";

export const NetworkConnectivityBanner = () => {
  const networkAvailable = useNetworkAvailable();

  if (!networkAvailable) {
    return (
      <SectionMessage appearance="warning" style={{ fontWeight: 600 }}>
        {getIntlContext().t("NetworkConnectivity.Offline")}
      </SectionMessage>
    );
  }

  return null;
};
