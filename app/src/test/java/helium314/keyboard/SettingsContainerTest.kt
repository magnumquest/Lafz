package helium314.keyboard

import helium314.keyboard.settings.SettingsContainer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class SettingsContainerTest {
    // Building the container throws if two settings use the same key, which crashes the whole settings app on launch.
    @Test fun `settings keys are unique`() {
        SettingsContainer(RuntimeEnvironment.getApplication())
    }
}
