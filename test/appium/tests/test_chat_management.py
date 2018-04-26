import pytest
import time

from tests import transaction_users
from tests.base_test_case import MultipleDeviceTestCase
from views.sign_in_view import SignInView


@pytest.mark.all
@pytest.mark.chat_management
class TestChatManagement(MultipleDeviceTestCase):

    @pytest.mark.testrail_case_id(3412)
    def test_delete_1_1_chat(self):
        self.create_drivers(2)
        device_1 = self.drivers[0]
        device_2 = self.drivers[1]
        user_1 = transaction_users['A_USER']
        user_2 = transaction_users['B_USER']

        device_1_sign_in_view = SignInView(device_1)
        device_1_sign_in_view.recover_access(user_1['passphrase'], user_1['password'])
        device_2_sign_in_view = SignInView(device_2)
        device_2_sign_in_view.recover_access(user_2['passphrase'], user_2['password'])

        device_1_home_view = device_1_sign_in_view.get_home_view()
        device_2_home_view = device_2_sign_in_view.get_home_view()

        # Device 1: Start new 1-1 chat
        device_1_home_view.add_contact(user_2['public_key'])
        device_1_chat_view = device_1_home_view.get_chat_view()
        chat_with_user_1 = device_2_home_view.get_chat_with_user(user_1['username'])
        chat_with_user_1.wait_for_element(30)
        device_2_chat_view = chat_with_user_1.click()

        # Devices: Request and send transactions
        transaction_amount = '0.00001'
        device_1_chat_view.request_transaction_in_1_1_chat(transaction_amount)
        device_1_chat_view.send_transaction_in_1_1_chat(transaction_amount, user_1['password'])
        device_2_chat_view.request_transaction_in_1_1_chat(transaction_amount)
        device_2_chat_view.send_transaction_in_1_1_chat(transaction_amount, user_2['password'])

        # Device 1: Send message to device 2
        device_1_message = 'message from user_1'
        device_1_chat_view.chat_message_input.send_keys(device_1_message)
        device_1_chat_view.send_message_button.click()

        # Device 2: Send message to device 1
        device_2_message = 'message from user_2'
        device_2_chat_view = device_2_home_view.get_chat_view()
        device_2_chat_view.chat_message_input.send_keys(device_2_message)
        device_2_chat_view.send_message_button.click()

        # Device 1: See the message from device 2
        device_1_chat_view.wait_for_message_in_one_to_one_chat(device_2_message, self.errors)

        # Stop device 2, it's not needed anymore
        device_2.quit()

        # Device 1: Delete chat and make sure it does not reappear after logging in again
        device_1_chat_view.delete_chat(user_2['username'], self.errors)
        device_1_profile_view = device_1_sign_in_view.profile_button.click()
        device_1_sign_in_view = device_1_profile_view.logout()
        time.sleep(5) # Prevent stale element exception for first_account_button
        device_1_sign_in_view.first_account_button.click()
        device_1_sign_in_view.sign_in(user_1['password'])
        assert not device_1_home_view.get_chat_with_user(user_2['username']).is_element_present(20)

        # Device 1: Start 1-1 chat with device 2
        device_1_chat_view = device_1_home_view.start_1_1_chat(user_2['username'])
        assert device_1_chat_view.no_messages_in_chat.is_element_present()

        self.verify_no_errors()
