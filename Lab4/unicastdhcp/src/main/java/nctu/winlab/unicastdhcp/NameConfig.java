package nctu.winlab.unicastdhcp;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

public class NameConfig extends Config<ApplicationId> {

  public static final String NAME = "serverLocation";

  @Override
  public boolean isValid() {
    return hasOnlyFields(NAME);
  }

  public String name() {
    return get(NAME, null);
  }
}
